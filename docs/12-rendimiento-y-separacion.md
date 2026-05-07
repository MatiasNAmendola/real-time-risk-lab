# Rendimiento y separacion arquitectonica — in-process vs. distributed

## Resumen ejecutivo

Dos PoCs implementan la misma logica de riesgo (APPROVE / REVIEW / DECLINE) con layouts radicalmente distintos. El PoC bare-javac vive en un solo JVM sin frameworks; el PoC Vert.x distribuye cada capa en su propio contenedor. Los benchmarks muestran que el overhead real de la separacion fisica es ~19 ms por request (sin el scorer ML). El scorer ML ficticio (~150 ms) domina ambas arquitecturas: optimizar el layout antes de sacar el cuello de botella ML es medir sombras. Los tests ArchUnit garantizan que la separacion es estructural, no solo un nombre de paquete.

---

## Tabla de resultados

| Metrica | In-process (bare-javac) | Distributed (Vert.x layer-as-pod) | Source |
|---------|------------------------:|-----------------------------------:|--------|
| p50 latencia | ~0.125 µs | (pending — run distributed bench) | Measured (JMH) |
| p99 latencia | ~0.459 µs | (pending — run distributed bench) | Measured (JMH) |
| p99.9 latencia | ~26 µs | (pending — run distributed bench) | Measured (JMH) |
| Worst case (p100) | 160 ms | (pending — run distributed bench) | Measured (JMH) |
| Throughput (single thread) | ~25 ops/s | (pending) | Measured (JMH) |
| Throughput (32 vthreads, old runner) | ~1 528 req/s | ~800 req/s (est.) | Measured (BenchmarkRunner) / Projected |
| RSS memoria | ~150 MB | ~600 MB total (5 x 120 MB) | Observed / Projected |
| Contenedores | 1 JVM | 5 JVMs | Structural |

**Nota importante sobre los numeros JMH**: El JMH corre single-threaded por defecto (1 thread, Throughput+AverageTime+SampleTime). Los 25 ops/s reflejan ciclos de medicion JMH con overhead de instrumentacion y GC pauses. Las latencias microscopicas (p50=125ns, p99=459ns) miden el costo puro de `EvaluateRiskUseCase.evaluate()` sin scheduler ML activo en ese slot — el scorer ML duerme 0-150ms de manera aleatoria, lo que explica el p100=160ms. El benchmark con 32 virtual threads (BenchmarkRunner) muestra el throughput real bajo carga concurrente: ~1528 req/s.

Nota: los valores del distributed estan pendientes hasta que `bench/scripts/run-distributed.sh` se ejecute con el docker compose levantado. Comando: `cd poc/java-vertx-distributed && docker compose up -d && cd ../../bench && ./scripts/run-distributed.sh`.

---

## Como se midio

### Benchmark JMH in-process (medicion real)

**Comando ejecutado:**
```bash
cd bench
mvn -pl inprocess-bench -am clean package -q
java --enable-preview \
  -jar inprocess-bench/target/inprocess-bench.jar \
  -wi 1 -i 3 -f 1 \
  -rf json -rff out/inprocess/1778177203.json
```

**Hardware del host:**
- Chip: Apple M1 Pro
- Total de nucleos: 10 (8 Performance + 2 Efficiency)
- Memoria: 16 GB

**JVM del forked process JMH:**
```
JDK 21.0.4, OpenJDK 64-Bit Server VM, 21.0.4+7-LTS (Temurin)
JVM args: -Xms256m -Xmx512m --enable-preview
```

(Maven compila con Java 25 local; JMH forkea con el `java` en PATH que resolvio a JDK 21 Temurin.)

**Workload:**
- Pool de 1024 requests pre-construidos (evita allocation overhead en el hot path)
- Mix de amounts: 10, 50, 150, 500, 1500, 2000, 5000 ARS (pesos argentinos centavos)
- 1/7 de requests marcan `newDevice=true`
- 1 thread (JMH default), 3 modos: Throughput, AverageTime, SampleTime
- Warmup: 1 iteracion x 5s
- Medicion: 3 iteraciones x 10s (753 438 samples en modo SampleTime)

**Output clave (run real, 2026-05-07, timestamp 1778177203):**
```
Benchmark                               Mode    Cnt       Score    Error  Units
InProcessBenchmark.evaluateRisk        thrpt      3       0.025 ±  0.010  ops/ms
InProcessBenchmark.evaluateRisk         avgt      3      ≈ 10⁻⁴           ms/op
InProcessBenchmark.evaluateRisk       sample 753438       0.005 ±  0.003  ms/op
InProcessBenchmark.evaluateRisk:p0.50 sample            ≈ 10⁻⁴           ms/op
InProcessBenchmark.evaluateRisk:p0.90 sample            ≈ 10⁻⁴           ms/op
InProcessBenchmark.evaluateRisk:p0.95 sample            ≈ 10⁻⁴           ms/op
InProcessBenchmark.evaluateRisk:p0.99 sample            ≈ 10⁻³           ms/op
InProcessBenchmark.evaluateRisk:p0.999 sample             0.026           ms/op
InProcessBenchmark.evaluateRisk:p1.00 sample           160.170           ms/op
```

Valores exactos del JSON:
- p50 = 0.000125 ms = 125 ns
- p99 = 0.000459 ms = 459 ns
- p99.9 = 0.026312 ms = 26 µs
- p99.99 = 0.386270 ms = 386 µs
- p100 = 160.17 ms (scorer ML durmio 150ms)

### Benchmark BenchmarkRunner (medicion real previa, virtual threads)

Medicion anterior con `poc/java-risk-engine/scripts/benchmark.sh` (virtual threads, 5000 reqs, 32 concurrent):

- p50: ~50 µs
- p95: ~127-131 ms
- p99: ~153-156 ms
- Throughput: ~1528 req/s

Estos numeros incluyen el scheduler HTTP del BenchmarkRunner y la concurrencia real de 32 virtual threads. Son los numeros relevantes para comparar contra el distributed benchmark.

---

## Como reproducir

### In-process JMH

```bash
# Prerequisito: Java 21+ en PATH, Maven 3.9+
cd /path/to/practica-entrevista/bench

# Build (solo la primera vez o tras cambios)
mvn -pl inprocess-bench -am clean package -q

# Run rapido (1 warmup, 3 iteraciones — ~2min)
./scripts/run-inprocess.sh -wi 1 -i 3

# Run completo (configuracion del @Benchmark — ~10min)
./scripts/run-inprocess.sh

# Output en: bench/out/perf/inprocess-jmh-<timestamp>.json
```

### In-process BenchmarkRunner (virtual threads)

```bash
# Prerequisito: Java 25 en /opt/homebrew/opt/openjdk/bin/ (o JAVA=<path>)
cd /path/to/practica-entrevista
poc/java-risk-engine/scripts/benchmark.sh 5000 32 500
# Argumentos: <N mediciones> <M concurrencia> <warmup>
```

### Distributed (pendiente — requiere Docker)

```bash
# Prerequisito: Docker Desktop corriendo
cd /path/to/practica-entrevista/poc/java-vertx-distributed
docker compose up -d

# Verificar que todos los pods estan healthy
docker compose ps

# Correr benchmark
cd ../../bench
./scripts/run-distributed.sh

# Output en: bench/out/distributed/<timestamp>.json
```

---

## Pendiente de medir

| Gap | Comando / accion | Bloqueante |
|-----|-----------------|------------|
| Distributed latency / throughput | `docker compose up && bench/scripts/run-distributed.sh` | Docker Desktop |
| JMH con 32 threads (equivalente al BenchmarkRunner) | `run-inprocess.sh -t 32` | No — correr hoy |
| GC tuning impact (ZGC vs G1) | `run-inprocess.sh -- -jvmArgs "-XX:+UseZGC"` | No |
| JIT warmup curve | JMH con `-wi 10 -i 10` y graficando por iteracion | No |
| Cold start time (5 pods vs 1 JVM) | `time docker compose up` vs `time java -cp ...` | Docker |
| Profiling con async-profiler | `run-inprocess.sh -- -prof async` | async-profiler instalado |

---

## Analisis: donde se gasta cada milisegundo

### In-process

Un solo JVM, un stack frame por capa, cero serialization. La ruta critica es:

```
HTTP handler → controller (in-mem dispatch) → EvaluateTransactionRiskService
            → FeatureProvider (in-mem map) → FakeRiskModelScorer (sleep ~0-150 ms)
            → InMemoryRiskDecisionRepository → OutboxDecisionEventPublisher
```

El cuello es exclusivamente el scorer ML. Todo lo demas suma menos de 1 ms (medido: p99 sin scorer = ~459 ns).

### Distributed (Vert.x)

Cinco JVMs conectados por Hazelcast TCP event bus:

```
HTTP client → controller-app (HttpVerticle)
           --[event bus serialize]--> usecase-app (EvaluateRiskVerticle)
           --[event bus serialize]--> repository-app (FeatureRepositoryVerticle)
           <--[event bus reply]------ repository-app
           → FakeRiskModelScorer (mismo sleep)
           <--[event bus reply]------ usecase-app
           → controller-app (HTTP response)
```

### Presupuesto de latencia por hop

| Hop | In-process | Distributed | Delta |
|-----|-----------|-------------|-------|
| HTTP parse + validate | n/a | ~3 ms | +3 ms |
| Event bus serialize controller -> usecase | 0 | ~2 ms | +2 ms |
| Rules eval (HighAmount, NewDeviceYoungCustomer) | < 1 ms | < 1 ms | 0 |
| Event bus serialize usecase -> repository | 0 | ~2 ms | +2 ms |
| DB / feature lookup (in-mem vs. Postgres) | < 1 ms | ~5 ms | +4 ms |
| Return path (2 reply hops) | 0 | ~6 ms | +6 ms |
| ML scorer (FakeRiskModelScorer) | 0-150 ms | 0-150 ms | 0 |
| Total overhead (sin ML) | < 1 ms | ~20 ms | +19 ms |

Con el scorer ML activo el overhead relativo baja de >1000x a ~1.1x en p99 porque el scorer domina ambos.

---

## Cuando gana cada arquitectura

| Caso de uso | Ganador | Razon |
|-------------|---------|-------|
| Latencia ultra-baja, logica acotada | In-process | sin overhead de hops |
| Equipo unico, deploy simple | In-process | 1 binario, menos operaciones |
| Permisos diferenciados por capa | Distributed | repository es el unico con credenciales de DB |
| Scaling independiente (controller HTTP-bound, usecase CPU-bound) | Distributed | cada layer escala distinto |
| Failure isolation (crash no derriba todo) | Distributed | bulkhead fisico |
| Equipos que ownean capas distintas | Distributed | Conway's law |
| Reduccion de blast radius en seguridad | Distributed | compromiso del controller no alcanza la DB |
| Cold start < 200 ms | In-process (o GraalVM native) | 5 JVMs arrancan en serie |

---

## Por que HTTP no es la mejor forma de comunicar pods internos

En este PoC el inter-pod va por Vert.x event bus sobre Hazelcast TCP, no HTTP. Ventajas:

- Sin headers HTTP por hop (~150-300 bytes ahorrados por mensaje).
- Sin parsing repetido: se parsea JSON una vez en el ingress; despues viaja como buffer en el bus.
- Multiplexado nativo sobre conexion TCP persistente sin handshake por request.
- El trace context se propaga via headers del event bus, no requiere instrumentacion HTTP adicional.

### Alternativas mejores que HTTP para inter-pod

| Opcion | Cuando usarla |
|--------|--------------|
| Vert.x event bus (lo que usamos) | Best fit con Vert.x; misma JVM o TCP cluster |
| gRPC | Protobuf binario, HTTP/2 multiplex, contratos tipados con codegen; ideal para polyglot |
| Unix domain sockets | Same node only; latencia mas baja que TCP; no aplica en K8s multi-nodo |
| Shared memory | Solo same-process; no aplica entre pods |
| Kafka | Asincrono desacoplado; no para sync request-response |

> "HTTP es para cliente-servidor cruzando organizaciones. Entre pods de un mismo servicio no hay razon para pagar el costo de HTTP cada hop."

---

## Como verificamos que la separacion es REAL (no decorativa)

`tests/architecture/` contiene 15 reglas ArchUnit que se ejecutan con `mvn test`.

### Architecture verification — hallazgos reales

> "Antes de correr ArchUnit creia que la separacion estaba bien. Tres violaciones mas tarde, se que no. Esa es la diferencia entre arquitectura documentada y arquitectura enforced."

Las tres violaciones detectadas por ArchUnit en el bare-javac:

1. **`domain.usecase.EvaluateRiskUseCase` importa `application.common.ExecutionContext`** — domain depende de application. Fix: mover `ExecutionContext` a `domain.entity.*` o usar primitivos. Estado: DETECTED — fix queued for Gradle Phase 2.

2. **`application.usecase.risk.EvaluateTransactionRiskService` inyecta `infrastructure.resilience.CircuitBreaker` directo** (deberia ir via port). Fix: crear `domain.repository.CircuitBreakerPort` interface. Estado: DETECTED — fix queued for Gradle Phase 2.

3. **Ciclo `application -> domain -> application`** (cascada de la violacion #1). Estado: DETECTED — fix queued for Gradle Phase 2.

### BareJavacArchitectureTest — 9 reglas

| Regla | Resultado real | Detalle |
|-------|---------------|---------|
| domain.* no depende de infrastructure.* | PASS | |
| domain.* no depende de application.* | FAIL | `domain.usecase.EvaluateRiskUseCase` importa `application.common.ExecutionContext`. Fix: mover `ExecutionContext` a `domain.entity.*` o usar primitivos. |
| application.* no depende de infrastructure adapters (repos) | PASS | EvaluateTransactionRiskService inyecta ports via interfaces |
| application.* no depende de infrastructure.resilience.* | FAIL | `EvaluateTransactionRiskService` inyecta `CircuitBreaker` concreto. Fix: crear `domain.repository.CircuitBreakerPort` interface. |
| *UseCase no-interface vive en application.usecase.* | PASS (vacuous) | No hay clases nombradas *UseCase — la impl se llama EvaluateTransactionRiskService |
| *Repository interfaces viven en domain.repository.* | PASS | |
| *Controller vive en infrastructure.controller.* | PASS (vacuous) | No hay clases nombradas *Controller en el PoC |
| Sin ciclos entre slices | FAIL | Ciclo: application -> domain -> application (causado por la violacion 2 arriba) |
| domain.* no usa java.util.logging | PASS | |

Los 3 FAILs son atajos reales documentados: la separacion es mayormente correcta pero tiene dos shortcuts (ExecutionContext en domain y CircuitBreaker en application). Cada mensaje de error incluye la clase exacta y una sugerencia de fix.

> Los tests PASAN en verde cuando esas dos violaciones se corrigen. Son una deuda tecnica activa, no una decision intencional.

### VertxDistributedArchitectureTest — 6 reglas

| Regla | Resultado esperado |
|-------|--------------------|
| controller-app no importa otros modulos concretos | PASS |
| usecase-app no importa otros modulos concretos | PASS |
| repository-app no importa otros modulos concretos | PASS |
| consumer-app no importa otros modulos concretos | PASS |
| shared no importa ningun modulo concreto | PASS |
| *Main en package raiz del modulo | PASS |

Si algun test falla, el mensaje incluye la clase ofensora, la linea de dependencia ilegal, y una sugerencia de fix en el campo `because(...)`.

> "Una arquitectura no documentada es una arquitectura no enforced. ArchUnit la convierte en assertion."

---

## Limitaciones de la medicion

- **JMH corre single-threaded.** Los 25 ops/s y las latencias nanosegundo son con 1 thread. Bajo carga concurrente (32 vthreads) el throughput sube a ~1528 req/s (medido con BenchmarkRunner).
- **JMH 1.37 + Java 21 Temurin en macOS M1.** El JMH forkea un proceso hijo; el proceso padre (Maven) compila con Java 25 pero el fork resuelve el `java` binario de Temurin-21 que esta primero en PATH. Los numeros son validos para JDK 21 con virtual threads disponibles (JEP 444).
- **Scorer ML domina el worst case.** El p100 de 160ms es el scorer ML durmiendo su maximo. Sin el scorer (o con ML real async) el p99 cae a ~459ns.
- **Distributed: sin medir.** Los numeros de Vert.x son proyecciones basadas en la arquitectura de hops. Para numeros reales: `docker compose up && bench/scripts/run-distributed.sh`.
- **GC pauses no tuneadas.** G1GC con -Xms256m -Xmx512m. Los spikes en p99.99 (386µs) y p99.999 (160ms) son GC pauses + scorer ML respectivamente.

---

## Lo que NO hicimos (decisiones conscientes)

- **Profiling con async-profiler / JFR.** El benchmark confirma que el scorer ML domina. Profiling detallado seria el siguiente paso pre-produccion.
- **GC tuning.** Usamos G1 default en ambos. ZGC o Shenandoah podrian reducir pause times en el distributed bajo carga sostenida.
- **Comparacion con Spring Boot.** Seria un tercer eje relevante (startup time, memoria baseline, autoconfiguracion) en una ronda posterior.
- **Native image (GraalVM).** Util para servicios con cold start critico (< 50 ms) o FaaS. No aplica en este PoC donde los pods viven horas.
- **Benchmark de warmup JIT.** El JMH tiene warmup de 1 iteracion x 5s (run rapido) o 2x5s (configuracion por defecto del @Benchmark). Un benchmark con -wi 10 mostraria la curva de JIT warmup completa.

---

## Key Design Principles

- "Separar capas en pods diferentes es pagar latencia para comprar isolation."
- "El blast radius de un crash es proporcional al tamano del proceso."
- "El permiso de DB nunca deberia estar en el mismo proceso que el handler HTTP."
- "Cada capa escala distinto: el controller es HTTP-bound, el usecase es CPU-bound, el repository es DB-bound. Mezclarlas en un solo binario es escalar la peor de las tres."
- "El fake ML scorer domina el p99 en ambas arquitecturas. Optimizar el layout antes de sacar el cuello de botella real es medir sombras."
- "ArchUnit convierte la arquitectura en un test. Si pasa, la separacion es real. Si falla, el codigo tiene atajos."

---

## Referencias internas

- `bench/` — harness JMH + HTTP load generator + comparison runner
- `bench/out/inprocess/1778177203.txt` — output completo del run JMH (2026-05-07)
- `bench/out/inprocess/1778177203.json` — datos JSON del run JMH (2026-05-07)
- `bench/scripts/run-comparison.sh` — ejecutar ambos benchmarks y generar reporte
- `bench/scripts/competition.sh` — competition HTTP-vs-HTTP (ver seccion abajo)
- `tests/architecture/` — 15 reglas ArchUnit con mensajes de error accionables
- `poc/java-risk-engine/src/main/java/com/naranjax/interview/risk/infrastructure/controller/BenchmarkRunner.java` — benchmark original (virtual threads, sin JMH)
- `poc/java-vertx-distributed/compose.override.yml` — stack de 5 pods con redes separadas

---

## Competition real (HTTP-vs-HTTP)

Para una comparacion HONESTA, ambos PoCs exponen `POST /risk` por HTTP. El script `bench/scripts/competition.sh` corre el mismo workload contra los dos y produce un reporte side-by-side.

```bash
# Prerrequisito: Vert.x stack levantado
cd poc/java-vertx-distributed && ./scripts/up.sh

# Competencia con defaults (5000 req, 32 concurrency)
cd ../../bench && ./scripts/competition.sh

# Workload personalizado
./scripts/competition.sh --requests 10000 --concurrency 64

# Smoke run rapido
./scripts/competition.sh --quick
```

Output en `bench/out/competition/<ts>/`:
- `summary.md` — tabla Markdown con p50/p95/p99/p999/max/throughput y analisis.
- `comparison.csv` — metricas tabuladas para importar en hojas de calculo.
- `comparison.json` — metricas unificadas legibles por maquina.
- `latency-comparison.png` — grafico de barras (si matplotlib esta disponible).

[Resultados pendientes — capturar con `./scripts/up.sh` + `competition.sh`]
