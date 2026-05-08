# 34 — Lecciones aprendidas

> Bugs no obvios que aparecieron en e2e #1, #2, #3, sus root causes, y los fixes aplicados. Cada uno tiene valor doble: para operar el repo (saber qué chequear cuando algo falla) y para entender el sistema (los detalles que las docs introductorias no cubren).

## Índice

| # | Tema | Síntoma | Doc relacionada |
|---|---|---|---|
| 1 | Vert.x EventBus advierte hostname random | POST → 502 Timeout reply 15s | ADR-0039 |
| 2 | AWS SDK init bloquea event loop | Primer POST timeout 14s | — |
| 3 | OpenObserve distroless sin healthcheck tools | Container "unhealthy" perpetuo | — |
| 4 | OpenBao healthcheck wget falla | depends_on bloquea apps | — |
| 5 | moto-init race silenciado | Secrets ausentes pero init exit 0 | — |
| 6 | ElasticMQ sin queues | Apps SQS fallan silenciosamente | — |
| 7 | Audit canonical terms con substring matching | 489 falsos positivos | — |
| 8 | Hazelcast flapping bajo memoria justa | Cluster forma 3 members → reparticiones | — |
| 9 | Cucumber ATDD con escenarios Karate | UndefinedStepException | — |
| 10 | Java 21 vs 25 — frameworks rezagados | JMH/Shadow/Karate no soportan classfile 25 | docs/26-java-version-compat-2026.md |

---

## 1. Vert.x EventBus advierte hostname random del contenedor

**Síntoma**: `POST /risk` retorna `502 Bad Gateway` con cuerpo `Timed out after waiting 15000(ms) for a reply. address: __vertx.reply.<uuid>, repliedAddress: usecase.evaluate`. Hazelcast forma cluster (`Members size: 3`) y los handlers `EvaluateRiskVerticle` / `FeatureRepositoryVerticle` están registrados.

**Cómo se detectó**: el `repliedAddress` apunta al destino original (no al reply UUID). Pista: `nc -zv usecase-app 5701` desde controller-app funciona, pero `getent hosts <container-id-hex>` retorna `NXDOMAIN`.

**Root cause**: el EventBus clusterizado de Vert.x abre un Netty TCP server propio (puerto random) y advertise `EventBusOptions.host` a los peers vía el cluster manager. Ninguno de los tres `Main` (`ControllerMain`, `UseCaseMain`, `RepositoryMain`) seteaba ese host, así que Vert.x defaultea a `InetAddress.getLocalHost().getHostName()` — dentro de un contenedor Docker eso es el container ID hex (ej. `182a8eec74d9`). El otro nodo recibe ese hostname por gossip y no puede resolverlo. `-Dhazelcast.local.publicAddress=service:5701` solamente arregla el gossip de Hazelcast, no el reply path del EventBus.

**Fix aplicado**: en cada `Main.java`:
```java
VertxOptions opts = new VertxOptions()
    .setEventBusOptions(new EventBusOptions()
        .setHost(ebHost)
        .setClusterPublicHost(ebHost));
```
con `ebHost = System.getenv().getOrDefault("EVENT_BUS_HOST", "<service-name>")`. Cada `Main` tiene su default (`controller-app`, `usecase-app`, `repository-app`).

**Verificación**: `curl -X POST :8080/risk -d @sample.json` → `200 {"decision":"REVIEW","reason":"ml score 0.5 > 0.4",...}` consistente en 150-360 ms post-warmup.

**Por qué es interesante**: el patrón `__vertx.reply.<uuid>` con `repliedAddress = target original` es el smoking gun de un reply path roto. La trampa es que conectividad TCP funciona (los nodos sí se ven), pero el host advertised por el cluster manager es inválido — siempre cross-checkear lo que el resolver del peer recibe, no lo que el inicio reporta. Además, fixar un solo nodo no alcanza: cada nodo en la cadena de reply tiene que advertise un host resolvible.

---

## 2. AWS SDK v2 inicializa sobre el event loop y dispara timeout en el primer POST

**Síntoma**: el primer `POST /risk` después de bootear demora ~14 s (timeout del cliente HTTP). Subsiguientes requests responden en 150-360 ms.

**Cómo se detectó**: `time curl -X POST localhost:8080/risk` en cold start vs warm; thread dump con `jstack` mostraba `eventloop-thread-0` adentro de `software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain.<init>` resolviendo IMDS / archivo de credenciales.

**Root cause**: `SecretsManagerClient.builder().build()` y `SqsClient.builder().build()` ejecutan resolución de region y credentials chain de forma sincrónica — leen archivos en `~/.aws/`, hacen lookups DNS, y en EC2 intentan IMDSv2. Si esa cadena se invoca desde un handler Vert.x, corre en el event loop y bloquea todos los demás requests hasta que termina.

**Fix aplicado**: warm-up explícito en el verticle de bootstrap, usando `vertx.executeBlocking(...)` antes de levantar el HTTP server. La construcción de los clientes AWS pasa al worker pool; el event loop queda libre. Adicionalmente, `aws.region` se inyecta por env (`AWS_REGION`) para que la cadena no haga lookups de provider en frío.

**Verificación**: primer `curl` post-boot retorna en <500 ms. `jstack` ya no muestra event loop dentro de la SDK.

**Por qué es interesante**: el reflejo de "en Vert.x todo lo que toca disco / red bloquea event loop" se aplica también a `<sdk>.builder().build()`, no sólo a las llamadas explícitas (`getSecretValue`). Construir clientes "perezosamente" la primera vez que se usan transfiere la latencia al primer request del usuario. Pre-warm en boot, en worker pool, es la fix idiomática.

---

## 3. OpenObserve distroless: el healthcheck Docker no puede ejecutarse

**Síntoma**: `docker inspect compose-openobserve-1` reporta `health=unhealthy`, `FailingStreak=36`, log `wget: executable file not found`. La aplicación escucha bien en `:5080/healthz` desde fuera del contenedor.

**Cómo se detectó**: `docker ps` muestra el container `unhealthy` indefinidamente; `docker exec compose-openobserve-1 sh` falla con `OCI runtime exec failed: no such file or directory: sh`.

**Root cause**: la imagen `public.ecr.aws/zinclabs/openobserve:latest` es distroless — no tiene shell, ni `wget`, ni `curl`, ni `nc`. Cualquier `HEALTHCHECK CMD ...` que use binarios falla porque Docker no encuentra el comando. El endpoint HTTP `/healthz` funciona perfecto, simplemente no es alcanzable desde adentro.

**Fix aplicado**: `compose/docker-compose.yml` líneas 147-154:
```yaml
healthcheck:
  test: ["NONE"]   # imagen distroless, readiness implícita por logs de boot
```
Con un comentario explicando el motivo.

**Verificación**: `docker inspect compose-openobserve-1 --format '{{.State.Status}} health={{.State.Health.Status}}'` → `running health=none`.

**Por qué es interesante**: distroless es la dirección recomendada para imágenes de prod (superficie de ataque mínima), pero rompe cualquier healthcheck CMD-based. Las opciones reales son: (a) sidecar con probe externa, (b) deshabilitar el healthcheck Docker y depender del orquestador (en K8s la `httpGet` probe lo hace nativo), (c) imagen no-distroless. El error `wget: executable file not found` redirige inmediatamente a la causa cuando se conoce este patrón.

---

## 4. OpenBao: healthcheck con `wget` rompe `depends_on: condition: service_healthy`

**Síntoma**: `compose-openbao-1` arranca, accept TLS, pero el healthcheck nunca pasa a `healthy`. Los servicios que tienen `depends_on: openbao: condition: service_healthy` se quedan colgados sin arrancar.

**Cómo se detectó**: `docker logs compose-openbao-1` muestra el server activo y respondiendo a `bao status` desde fuera. `docker inspect` reporta health failing por exit code del healthcheck CMD.

**Root cause**: la imagen `openbao/openbao` no incluye `wget` ni `curl`. El healthcheck por defecto / heredado de templates Vault asume `wget`. El binario propio (`bao`) sí está disponible — `bao status` retorna 0 si está unsealed, 2 si sealed, así que sirve como readiness.

**Fix aplicado**: reemplazar el healthcheck por:
```yaml
healthcheck:
  test: ["CMD", "bao", "status"]
  interval: 5s
  timeout: 3s
  retries: 20
  start_period: 10s
```

**Verificación**: `docker inspect compose-openbao-1 --format '{{.State.Health.Status}}'` → `healthy` < 30 s post-up. Los apps que dependen pasan a `started`.

**Por qué es interesante**: misma clase de bug que el #3 (imagen minimalista, herramientas asumidas que no están), pero con un workaround distinto: usar el CLI propio del producto. El reflejo correcto es revisar siempre `docker exec <c> ls /usr/bin /bin` antes de copiar healthchecks de templates genéricos.

---

## 5. moto-init: race condition + `except Exception` silencia errores de seeding

**Síntoma**: `moto-init` exit 0, compose sigue al up, pero las apps fallan con `ResourceNotFoundException: Secrets Manager can't find the specified secret` al primer request real. Los secrets nunca se crearon, pero el init no se quejó.

**Cómo se detectó**: `aws --endpoint http://localhost:4566 secretsmanager list-secrets` retorna `SecretList: []` después del up. El log de `moto-init` decía "Secrets seeded" — era mentira.

**Root cause**: dos bugs combinados.
1. Race: el container `moto-init` arrancaba apenas el healthcheck de moto pasaba, pero el endpoint de Secrets Manager dentro de moto no estaba listo todavía. El primer `boto3.client('secretsmanager')` tiraba `EndpointConnectionError`.
2. El script tenía `try: ... except Exception: pass` envolviendo todo el seeding. El connect-error se tragaba; el script imprimía "done" y exit 0.

**Fix aplicado**: `compose/docker-compose.yml` líneas ~207-280:
- Wait-for explícito (30 s) por `urllib` contra `http://moto:5000/moto-api/` antes de cualquier llamada `boto3`.
- Entrypoint cambiado de `/bin/sh -c` a `/bin/sh -ec` (set -e) — falla loud.
- `except Exception` reemplazado por catch específico de `ResourceExistsException` (idempotencia). Cualquier otra excepción re-raises.
- `describe_secret` post-create de cada secret; FATAL exit 2 si alguno falta.
- Healthcheck de moto bumpeado: `interval: 5s, retries: 20, start_period: 20s`.

**Verificación**: `aws --endpoint http://localhost:4566 secretsmanager list-secrets | jq '.SecretList | length'` → `3` (`riskplatform/db-password`, `risk-engine/db-password`, `risk-engine/api-key`).

**Por qué es interesante**: `except Exception` en scripts de init es un anti-patrón clásico que oculta exactamente los errores que importan (connectividad de infra). El healthcheck de un mock no garantiza que cada subservicio esté listo — moto en particular boota la API HTTP antes que sus backends estén respondiendo. Wait-for por endpoint específico es la fix correcta.

---

## 6. ElasticMQ arranca sin queues; las apps fallan silenciosamente

**Síntoma**: el SQS publisher (`SqsDecisionPublisher`) loguea `MessageId=null` o tira `QueueDoesNotExist` en una thread async que no propaga. El servicio sigue respondiendo HTTP 200 al `/risk` request porque el publish es fire-and-forget.

**Cómo se detectó**: `aws --endpoint http://localhost:9324 sqs list-queues` → `{}`. Logs del consumer en otra carpeta del proyecto reportaban 0 mensajes consumidos.

**Root cause**: ElasticMQ arranca con su config built-in vacía. No hay un init de seeding. El nombre de queue (`risk-decisions-queue`) está hard-coded en `compose.override.yml` (`RISK_SQS_QUEUE`) y en `SqsDecisionPublisher.java` (`DEFAULT_QUEUE`), pero nadie las creaba.

**Fix aplicado**: container `elasticmq-init` nuevo (líneas ~292-326), pattern espejo de `moto-init` y `minio-init`. Imagen `amazon/aws-cli:latest`. Crea idempotentemente: `risk-decisions-queue`, `risk-decisions-dlq`, `risk-audit-queue` (`get-queue-url` para skip si existe, `create-queue` si no). Verifica con `list-queues` post-create; FATAL exit 2 si alguna falta.

**Verificación**: `aws --endpoint http://localhost:9324 sqs list-queues` → 3 queues. Decisiones publicadas reciben `MessageId` válido.

**Por qué es interesante**: fire-and-forget en publishers SQS oculta este tipo de bug — el endpoint funciona, el HTTP responde 200, pero el side effect crítico (audit) se pierde. La fix doble (init container + verify) es coherente con el patrón ya establecido para `moto-init` y `minio-init`. La lección operativa: en infra mockeada, todo recurso usado por código debe tener un init container que lo cree con verify.

---

## 7. Audit de canonical terms: substring matching produce 489 falsos positivos

**Síntoma**: `./nx audit consistency` reporta 524 violaciones de terminología (88% de score perdido). La inspección manual muestra que las "violaciones" son frases legítimas en español.

**Cómo se detectó**: `grep -n "regla" vault/04-Concepts/*.md | wc -l` → 165 hits, todos en frases tipo "regla de dependencias" (relación arquitectónica, no un término del glosario).

**Root cause**: `consistency-auditor.py audit_terms()` usa `if alias in line` (substring, case-sensitive). Combinado con el alias-set existente, tres clases de falsos positivos:
1. Aliases de una palabra colisionan con prosa: `regla`, `outbox`, `vertx-distributed`.
2. Aliases auto-colisionan con su propio canonical: `outbox` ⊂ `outbox pattern`, `bare-javac` ⊂ `PoC bare-javac`.
3. Aliases lowercase (`openobserve`, `testcontainers`) matchean URLs y hostnames legítimos.

**Fix aplicado**: `.ai/audit-rules/terminology.yaml` curado. Removidos los aliases problemáticos con un campo `note:` explicando cada decisión. Header del archivo documenta la restricción del matcher (substring) y la política bilingüe: términos de dominio admiten variantes narrativas en español sin alias; términos técnicos universales (outbox, circuit breaker, idempotency) mantienen canonical en inglés. Cero ediciones a `vault/`.

**Verificación**: `./nx audit consistency` → `TERMS: 0 violations, score 100%`. Score global subió 67.8% → 70.0%.

**Por qué es interesante**: el caso clásico de "arregla los datos antes de tocar el código". Un parser custom + matcher naive produce falsos positivos masivos en cuanto el corpus se traduce a otro idioma. La fix correcta es curar las reglas (YAML), no editar centenas de archivos. Subnota: el parser YAML del auditor no soporta `aliases: []` (lo lee como string `"[]"`) — workaround: omitir la key cuando no hay aliases. Bug del parser, anotado para refactor futuro.

---

## 8. Hazelcast flapping bajo presión de memoria (Docker Desktop con RAM justa)

**Síntoma**: el cluster Vert.x forma `Members size: 3`, luego un nodo se va y vuelve, y el partition table reparticiona constantemente. Logs muestran `MIGRATION` events cada 10-30 s; los EventBus replies tardan más de lo normal porque los handlers se mueven entre nodos.

**Cómo se detectó**: `docker stats` durante el up muestra los tres apps cerca del límite de memoria del compose. Logs de Hazelcast: `Member ... is suspected: ...` seguido por `Member ... left` y `Member ... joined`.

**Root cause**: cada app Vert.x con JVM heap default + Hazelcast embebido + Netty + AWS SDK consume ~600 MB. Con tres apps + moto + postgres + kafka + openobserve en Docker Desktop con 4 GB asignados, el OS swappea, las JVMs hacen GC pauses largos, y Hazelcast los detecta como sospechosos por timeout en heartbeat (default 5 s). Cuando vuelven, el partition rebalancing arranca de cero.

**Fix aplicado**: dos palancas combinadas:
1. Cap de heap explícito por app: `-Xmx512m` en el `JAVA_TOOL_OPTIONS` del compose, evita GC pressure por sobrecommit.
2. Hazelcast heartbeat tolerance subida vía env: `HZ_NETWORK_FAILURE_DETECTOR_HEARTBEAT_TIMEOUT_SECONDS=30` (default 5). Con esto un GC pause de 10 s no dispara suspicion.
3. Documentado en el README del PoC: requiere ≥6 GB asignados a Docker Desktop / OrbStack para el stack completo.

**Verificación**: `docker logs compose-controller-app-1 | grep -E "MIGRATION|suspected"` durante 5 minutos post-up → 0 eventos después del partition table inicial.

**Por qué es interesante**: el flapping no se manifiesta como un error explícito, se manifiesta como latencia variable en p99 y reply timeouts ocasionales. La pista es leer logs de Hazelcast y correlacionar con `docker stats`. El fix tiene dos capas: una estructural (heap cap) y una de tolerancia (heartbeat). Cualquiera por sí sola no alcanza.

---

## 9. Cucumber-JVM no entiende escenarios escritos en sintaxis Karate

**Síntoma**: `./gradlew :poc:vertx-layer-as-pod-eventbus:atdd-tests:test -Patdd` falla con `UndefinedStepException` en 10 escenarios. Los step definitions parecen estar registrados correctamente.

**Cómo se detectó**: `./gradlew test` log muestra steps undefined con cuerpos como `* url 'http://localhost:8080'`, `* path '/risk'`, `And match response.decision == 'REVIEW'`.

**Root cause**: los `.feature` afectados estaban escritos en sintaxis **Karate** (DSL declarativo: `* url`, `* path`, `match response`), no en Gherkin estándar. Cucumber-JVM 7 sólo entiende Gherkin (`Given`, `When`, `Then`, `And`). El `*` de Karate es legal en Gherkin como sinónimo de step bullet, pero el cuerpo (`url '...'`, `match ... == ...`) no tiene step definition en Cucumber porque eso es vocabulario de Karate.

**Fix aplicado**: separación clara por suite.
- `tests/risk-engine-atdd/` (Cucumber-JVM 7): `.feature` reescritos a Gherkin idiomático con steps custom (`Given un cliente con score X`, `When envío POST a /risk`, `Then la decisión es REVIEW`). Step defs en `RiskSteps.java`.
- `poc/vertx-layer-as-pod-http/atdd-tests/`: `.feature` Karate-native, corren con runner de Karate.
- README de cada suite explica qué dialecto usar y por qué.

**Verificación**: `./gradlew :poc:vertx-layer-as-pod-eventbus:atdd-tests:test -Patdd` → 0 undefined, todos los escenarios pasan o fallan con asserts reales.

**Por qué es interesante**: Karate y Cucumber comparten extensión `.feature` y ambos parsean Gherkin de boca, pero el espacio de steps es disjunto. Mezclar dialectos en el mismo runner es el error. La regla R3 del repo (ATDD primero) elige una herramienta por contexto: Karate cuando hay HTTP / GraphQL externo, Cucumber cuando los step definitions encapsulan lógica de dominio. La separación por carpeta + dialecto es la fix estructural.

---

## 10. Java 21 vs 25: frameworks de build no soportan classfile 25

**Síntoma**: `gradle build` con `--release 21` produce errores en cuatro lugares distintos: JMH benchmarks corren pero `java -jar benchmarks.jar` dice "No matching benchmarks"; Shadow plugin tira `Class major version 69 not supported`; Karate 1.4 tira `IllegalAccessException` sobre `permits`.

**Cómo se detectó**: probar cada herramienta del build pipeline contra `release 25` y enumerar fallas. Documentado in extenso en `docs/26-java-version-compat-2026.md`.

**Root cause**: ADR-0001 declara Java 25 LTS como canonical. La realidad del ecosistema a 2026-05 es:
1. JMH 1.37 annotation processor lee class files internamente; no entiende attributes nuevos (sealed types, value types preview en classfile 25 / major 69). Genera classes pero no escribe `META-INF/BenchmarkList`.
2. `com.gradleup.shadow:8.x` parser de constant pool falla con tags desconocidos.
3. Karate 1.4.x usa reflection sobre `permits` y tira `IllegalAccessException` con bytecode 25.
4. Plugins legacy de Gradle asumen `<= 21`.

**Fix aplicado**: `riskplatform.java-conventions.gradle.kts`:
```kotlin
java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }
tasks.withType<JavaCompile>().configureEach { options.release.set(21) }
```
Compilamos con `--release 21`, ejecutamos en cualquier JDK 21+ (incluyendo 25). ADR-0001 se mantiene; este es un workaround temporal documentado en `docs/26-java-version-compat-2026.md`.

**Verificación**: `gradle build` clean en los cuatro módulos; `java -jar benchmarks.jar` lista benchmarks; `./gradlew test -pl karate-tests` pasa.

**Por qué es interesante**: ADRs aspiracionales chocan con realidad de ecosistema. La decisión arquitectónica correcta (Java 25 LTS) no es la decisión operativa correcta cuando 4 de 4 herramientas del build pipeline no soportan classfile 25. La fix honesta es documentar el delta, no torcer la realidad. El compromiso `--release 21` aprovecha lo bueno (sealed interfaces, records, pattern matching switch) sin entrar en territorio que rompe tooling. Cuando JMH/Shadow/Karate alcancen, se levanta el cap. El doc `26` sirve para que el reviewer técnico vea que la decisión está medida, no improvisada.

---

## 11. Custom Java load bench mentía en el p99 — k6 (Grafana) lo destapó

**Síntoma**: `bench/distributed-bench` reportaba consistentemente p99 ~ 180ms para `vertx-layer-as-pod-eventbus` bajo 32 concurrency. Las gráficas de OpenObserve sobre tracing OTEL del mismo período mostraban p99 ~ 290ms. Diferencia de 110ms entre dos mediciones del mismo intervalo, mismo workload.

**Cómo se detectó**: corrida de comparación `./nx bench k6 load --target vertx-layer-as-pod-eventbus --duration 2m` con `K6_PROMETHEUS_RW_SERVER_URL` apuntando a OpenObserve. El p99 que reportó k6 (286ms) era casi idéntico al p99 que ya mostraba OpenObserve sobre los spans del controller. El custom bench reportaba ~180ms para la misma corrida.

**Root cause**: el percentile calculator del custom bench mantenía un array bounded de las últimas N samples (N=1000) y computaba percentiles sobre eso. Bajo carga sostenida con cola de respuestas en el servidor, las muestras de long-tail (las pocas requests que pegaban con un GC pause o un partition rebalancing de Hazelcast) se evictaban del array antes del cálculo final. Resultado: p99 sub-reportado por ~ 40%. Adicionalmente, el bench usaba `System.nanoTime()` antes de `socket.connect()` y después de `read()`, pero no separaba TLS handshake, lo que ya de por sí inflaba la varianza.

**Fix aplicado**: adoptar k6 como tool oficial de load HTTP. Razones técnicas, no de moda:
1. k6 usa HDR-style histograms internamente (`metrics.Trend`) → percentiles correctos sobre todo el dataset, no sobre una ventana.
2. Output múltiple en una corrida (stdout summary + JSON + Prometheus RW) → mismo dato consultable desde tres ángulos.
3. Threshold gating en JS (`http_req_duration: ['p(99)<300']`) → exit code 99 si falla, CI puede fallar el build.
4. Stages nativos para ramps (smoke/load/stress/spike/soak son archetypal) → no se reinventan timers.
5. Single binary Go, sin JVM warmup → el bench arranca en < 100ms, lo que importa cuando lo corrés como pre-deploy gate.

JMH (`bench/inprocess-bench`) se mantiene para microbench in-process (sin red, sin serialización), que es exactamente donde JMH es state-of-the-art.

**Verificación**: `./nx bench k6 competition load` corre el mismo scenario contra los 4 servicios y emite `out/k6-competition/<ts>/comparison.md` con p95/p99/error_rate/reqs por target. Los números cuadran con OpenObserve dentro de ±5ms.

**Por qué es interesante**: este es el clásico caso de "tool homegrown que pasaba el smoke pero no era trustworthy en producción". El bench custom no estaba mal escrito — estaba mal especificado. El requirement era "p99 sostenido" y la implementación entregaba "p99 de la última ventana". Industry-standard tools (k6, wrk2, vegeta) ya resolvieron este problema con histogramas correctos. Reinventar load testing con un thread pool es un anti-pattern en 2026. Documentado en [ADR-0040](../vault/02-Decisions/0040-k6-for-load-testing.md).

---

## 12. `./gradlew clean build` por default infló duración de runs y causó contention multi-agente

**Síntoma**: tres builds simultáneos en una sesión multi-agente. `gradlew clean build -x test` corriendo 5:49 min, `shadowJar` con `--no-build-cache --no-configuration-cache` corriendo 1:14 min, daemon en idle. CPU al 100% sostenido, IDE laggy, otros agentes en cola.

**Cómo se detectó**: `ps -ef | grep gradle` mostró tres procesos compitiendo por el mismo daemon. Cada `clean` invalidaba el build cache antes de empezar, así que los 200+ tasks se rebuildeaban from scratch aunque la mayoría de las invocaciones solo necesitaban el shadowJar de un módulo puntual (ej: `bench-distributed`).

**Root cause**: tres errores acumulados:
1. `nx build` exec-eaba `./gradlew clean build` sin condición. Default lento, sin escape hatch para "solo si cambió".
2. Algunos scripts forzaban `--no-build-cache --no-configuration-cache` "por seguridad", anulando los caches que Gradle ya hace bien.
3. Cero coordinación entre agentes: dos invocaciones simultáneas del mismo target compiten por el daemon, doblando wall-clock.

**Fix aplicado**:
- `nx_build_target` en `nx`: build incremental con jar-freshness check (mtime jar vs newest source). Skip si los jars son newer que el último source. `--rebuild` o `NX_REBUILD=1` para forzar.
- Lock por target (`.gradle/.nx-build-locks/<target>.lock` con PID-liveness check) para coordinar agentes paralelos.
- `bench/scripts/run-comparison.sh` skipea Gradle si los jars existen.
- Backward-compat: `./nx build --legacy-clean` mantiene el comportamiento original para CI/debugging.
- Default flags en builds: `--build-cache --configuration-cache --parallel`. Removidos los `--no-*` flags salvo en debugging explícito.

**Verificación**:
```bash
./gradlew :poc:vertx-monolith-inprocess:clean :poc:vertx-monolith-inprocess:shadowJar
time ./nx build vertx-monolith-inprocess               # primer build, ~3-5 min
time ./nx build vertx-monolith-inprocess               # warm, < 2s (skip)
touch poc/vertx-monolith-inprocess/src/main/java/io/riskplatform/monolith/Application.java
time ./nx build vertx-monolith-inprocess               # incremental, ~15-30s
time NX_REBUILD=1 ./nx build vertx-monolith-inprocess  # forced, ~3-5 min
```

**Por qué es interesante**: anti-pattern común en monorepos: scripts heredan un default conservador (`clean build`) "para evitar problemas de cache" que dobla cada feedback loop. El cache de Gradle es robusto — su breakage es la excepción, no la regla. Cuando el cache se rompe (rarísimo), `--rebuild` lo arregla en 30s. Cuando se rompe `clean build` por default, lo pagás en cada iteración. Topic engram: `build-perf-smart-cli`.

---

> Cada gotcha de este doc tiene su evidencia en `out/e2e-verification/` y su trazabilidad en Engram (topic keys: `hazelcast-eventbus-fix`, `infra-init-fixes`, `fixes-openobserve-audit-orphans`, `canonical-terms-bilingual`, `k6-load-testing-integration`, `build-perf-smart-cli`). Para reproducir cada fix: `git log --grep "<topic-key>"`.
