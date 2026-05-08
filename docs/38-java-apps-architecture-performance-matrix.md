# 38 — Matriz de apps Java, arquitectura y performance

**Fecha:** 2026-05-08  
**Scope:** apps Java bajo `poc/`, librerías compartidas `pkg/`/`sdks/`, y narrativa de discusión técnica para Risk Decision Platform.  
**Tesis:** mismo dominio de decisión de riesgo, distintas topologías/stacks para hacer visible el costo de cada decisión arquitectónica.

---

## 1. Resumen ejecutivo

Las apps Java no deberían venderse como productos distintos. Deben explicarse como **variantes comparables del mismo problema**:

```text
POST /risk
  -> validar request / correlationId
  -> enriquecer features
  -> aplicar reglas / scorer / budget
  -> devolver APPROVE | REVIEW | DECLINE
  -> emitir auditoría/evento async
```

Lo que cambia entre PoCs es el **adapter/topología**:

1. Java puro in-process.
2. Vert.x en una sola JVM.
3. Vert.x distribuido por capas vía EventBus clustered.
4. Vert.x distribuido por pods HTTP + tokens.
5. Vert.x service-to-service real entre bounded contexts.

La demo más honesta es: **misma lógica conceptual, distinto overhead, distinto aislamiento, distinta complejidad operativa**.

---

## 2. Inventario real de apps Java

| App | Path | Tipo | Comunicación interna | Puerto demo | Propósito |
|---|---|---|---|---:|---|
| `java-risk-engine` | `poc/java-risk-engine` | Java 21, Clean Architecture, sin framework HTTP externo | Métodos/in-memory; HTTP stdlib opcional | 8081 | Baseline de lógica pura y menor overhead. |
| `java-monolith` | `poc/java-monolith` | Vert.x single JVM | EventBus local / verticles in-process | 8090 | Mostrar Vert.x sin costo de red entre capas. |
| `java-vertx-distributed` | `poc/java-vertx-distributed` | Vert.x + Hazelcast, 4 JVMs layer-as-pod | Clustered EventBus TCP | 8080 | Separar controller/usecase/repository/consumer como capas aisladas. |
| `vertx-risk-platform` | `poc/vertx-risk-platform` | Vert.x, 3 pods HTTP | HTTP inter-pod + `x-pod-token` | 8180 | Contrastar HTTP explícito/debuggable vs EventBus. |
| `service-mesh-demo` | `poc/service-mesh-demo` | Vert.x + Hazelcast, bounded contexts | EventBus RPC/async entre servicios Vert.x | 8090 | Service-to-service real en Vert.x: bounded contexts comunicados por verticles/EventBus. |

> Nota de precisión: `java-vertx-distributed` es **layer-as-pod** de un mismo bounded context, no microservicios de negocio. `service-mesh-demo` es el PoC de bounded contexts reales dentro del stack Vert.x. Vert.x aporta verticles/EventBus; la topología de pods es decisión del PoC, no una obligación del framework.

---

## 3. Qué comparten hoy

### Librerías compartidas disponibles

El `settings.gradle.kts` incluye módulos compartidos que sirven como base común:

| Módulo | Rol esperado |
|---|---|
| `pkg:risk-domain` | Reglas, engine, configuración de reglas, modos barrier/shadow/circuit. |
| `pkg:errors` | Errores comunes. |
| `pkg:resilience` | Resiliencia reutilizable. |
| `pkg:events` | Eventos/outbox/event abstractions. |
| `pkg:kafka` | Utilidades Kafka/Redpanda. |
| `pkg:observability` | Logging/tracing/metrics base. |
| `pkg:repositories` | Adapters o helpers de persistencia. |
| `sdks:risk-events` | Contratos de eventos compartidos. |
| `poc:java-vertx-distributed:shared` | DTOs/contratos específicos del PoC distribuido, hoy reutilizados por `java-monolith`. |

### Estado actual de paridad

| Aspecto | Estado |
|---|---|
| Dominio conceptual | Compartido: decisión de riesgo APPROVE/REVIEW/DECLINE. |
| Implementación física | Parcialmente compartida; todavía hay duplicación por PoC. |
| DTOs | Parcialmente compartidos vía `java-vertx-distributed:shared` y SDKs. |
| Reglas | Hay `pkg:risk-domain`, pero no todo PoC consume exactamente el mismo pipeline. |
| Observabilidad | El patrón es común, la profundidad varía por PoC. |
| Benchmarks | Existen bases (`bench/`, k6, JMH), pero falta matriz empírica completa lado a lado. |

Conclusión: la narrativa ya existe, pero falta consolidarla con un contrato común de benchmark y una matriz de runs repetibles.

---

## 4. Matriz arquitectónica comparativa

| Dimensión | Bare Java | Vert.x monolith | Vert.x layer-as-pod | Vert.x HTTP pods | Service-to-service |
|---|---|---|---|---|---|
| Proceso/JVM | 1 | 1 | 4 | 3 | 4 |
| Unidad de separación | Paquetes Clean Arch | Verticles/capas | Capas como pods | Capas como pods HTTP | Bounded contexts |
| Hop entre capas | Método | EventBus local | EventBus clustered | HTTP | EventBus RPC/async |
| Serialización interna | No | Baja/local | Sí | Sí/JSON HTTP | Sí |
| Debug con curl | Sí, borde HTTP | Sí, borde HTTP | Solo borde HTTP | Sí, cada pod HTTP | Borde HTTP + logs/EventBus |
| Aislamiento de fallo | Bajo | Bajo | Medio/alto por capa | Medio/alto por pod | Alto por bounded context |
| Escalado independiente | No | No | Por capa | Por capa | Por servicio de negocio |
| Complejidad operativa | Baja | Media | Alta | Media/alta | Alta |
| Mejor uso demo | Latencia base | Vert.x sin red | Costo/beneficio de aislar capas | Transparencia/permisos HTTP | Microservicios reales |

---

## 5. Matriz de performance esperada

Esta tabla es **hipótesis arquitectónica**, no resultado final medido. Los valores finales deben salir de `./nx bench k6 competition ...`, JMH y runs documentados.

| Stack | Latencia esperada | Throughput esperado | Causa principal | Riesgo |
|---|---|---|---|---|
| Bare Java | Mejor p50/p99 para lógica pura | Alto para CPU/light I/O | Sin framework ni red interna | Menos parecido a producción distribuida. |
| Vert.x monolith | Muy cerca del bare en hot path I/O | Alto | Event loop + EventBus local, sin hops TCP | Blast radius de una JVM. |
| Vert.x EventBus clustered | Mayor que monolith | Alto si el I/O no bloquea | Serialización + TCP + cluster manager | Tuning EventBus/Hazelcast, opacidad de debugging. |
| Vert.x HTTP pods | Mayor que EventBus en general | Bueno, menor por overhead HTTP/JSON | HTTP parse/serialize por hop | Más simple de observar, pero más costo por request. |
| Service-to-service | Variable | Depende de fan-out y timeouts | RPC a fraud-rules/ML + audit async | Latencia compuesta y fallos parciales. |

Orden esperado de menor overhead en la ruta crítica:

```text
bare Java ~= Vert.x monolith
  < Vert.x EventBus clustered
  < Vert.x HTTP inter-pod
  < service-to-service con fan-out sync
```

Con una advertencia clave: si el scorer ML simulado consume 100–150 ms, ese dependency domina el p99 y puede ocultar diferencias de 1–20 ms entre stacks.

---

## 6. Por qué Vert.x es una buena pieza para evidenciar estos trade-offs

Según la documentación oficial de Eclipse Vert.x:

- Sus APIs son mayormente **event-driven** y llaman handlers asincrónicamente cuando hay eventos disponibles. [Fuente: Vert.x Core docs](https://vertx.io/docs/vertx-core/java/).
- Con pocas excepciones, las APIs de Vert.x **no bloquean el thread llamante**; eso permite manejar mucha concurrencia con pocos threads. [Fuente](https://vertx.io/docs/vertx-core/java/).
- Vert.x usa un patrón **multi-reactor**: una instancia mantiene varios event loops y puede escalar dentro de un proceso multi-core. [Fuente](https://vertx.io/docs/vertx-core/java/).
- El EventBus soporta **publish/subscribe, point-to-point y request-response**, útil para modelar controllers, usecases, repositories y consumidores sin acoplarlos por llamadas directas. [Fuente](https://vertx.io/docs/vertx-core/java/).
- El EventBus puede ser **clustered**: varias instancias Vert.x en red pueden formar un bus distribuido. [Fuente](https://vertx.io/docs/vertx-core/java/).
- Vert.x expone SPI de **métricas y tracing**, lo que encaja con la regla del repo de OTEL en cada request. [Fuente](https://vertx.io/docs/vertx-core/java/).
- La propia documentación advierte la regla de oro: **no bloquear el event loop**. Esto es valioso para discusión técnica porque permite explicar cómo se rompen estos sistemas bajo JDBC bloqueante, sleeps, locks o CPU pesado en handlers. [Fuente](https://vertx.io/docs/vertx-core/java/).

Traducción al caso de fraude:

| Beneficio Vert.x | Cómo se evidencia en el repo |
|---|---|
| Pocos threads para mucha concurrencia I/O | HTTP + EventBus + Kafka/DB async sin thread-per-request clásico. |
| EventBus local o distribuido | `java-monolith` vs `java-vertx-distributed`. |
| Request-response interno | `controller-app -> usecase-app -> repository-app`. |
| Pub/sub / async downstream | Audit/eventos que no bloquean decisión. |
| Multi-reactor | Una JVM puede explotar varios cores sin levantar N procesos tipo Node.js. |
| Observabilidad | OTel/OpenObserve para ver spans por hop. |
| Tuning explícito | EventBus options, Hazelcast config, worker pool, blocked thread checker. |

---

## 7. Agrupación correcta de lógica en Clean Architecture

> Cuidado de wording: evitar frases como “Vert.x agrupa todos los controllers/repositories en el mismo pod”. Lo correcto es “el PoC puede desplegar verticles por rol/capa/bounded context; Vert.x habilita la comunicación, pero no impone la agrupación”.


No conviene crear un servicio por cada “tipo de configuración”. La separación debe responder a una razón arquitectónica. En particular, `service-mesh-demo` no significa “un servicio por layer”; significa “un servicio por bounded context usando verticles/EventBus. No afirmamos que Vert.x obligue a agrupar controllers/usecases/repositories en pods.”

### A. Layer-as-pod

En `java-vertx-distributed` usamos Vert.x para demostrar una topología **layer-as-pod**. Es decir: los verticles/adapters de controller, usecase, repository y consumer se despliegan en procesos/pods separados y se comunican por Vert.x clustered EventBus.

```text
controller-app    -> verticles/adapters HTTP inbound
usecase-app       -> verticles de aplicación / casos de uso
repository-app    -> verticles/adapters de persistencia y feature store
consumer-app      -> verticles consumers async
```

Esta topología es una decisión del PoC, no una recomendación obligatoria de Vert.x. La usamos para evidenciar:

- aislamiento por permisos: solo repository-app necesita credenciales de DB/cache;
- enforcement físico de boundaries Clean Architecture;
- blast radius por capa;
- posibilidad de escalar capas con perfiles distintos;
- costo de serializar y cruzar red entre capas;
- diferencia entre EventBus local y clustered EventBus.

No debe describirse como microservicios de negocio. Es una PoC de separación técnica por layers para medir trade-offs, no una recomendación productiva por defecto.

### B. Bounded contexts reales en Vert.x

`service-mesh-demo` separa por capacidad de negocio, no por layer:

```text
risk-decision-service  -> controller + usecases + repositories del contexto decisión
fraud-rules-service    -> consumers/controllers + usecases + repositories del contexto reglas
ml-scorer-service      -> consumers/controllers + usecases + repositories del contexto scoring
audit-service          -> consumers + usecases + repositories del contexto auditoría
```

La idea es demostrar comunicación **Vert.x a Vert.x** entre componentes/servicios que comparten el mismo modelo técnico: EventBus, verticles, DTOs y configuración de cluster. La forma exacta de empaquetar controllers, usecases y repositories queda como decisión de arquitectura del PoC, no como recomendación obligatoria de Vert.x.

Esto sirve para demostrar:

- ownership por dominio;
- fan-out sync con budgets/timeouts;
- audit async que no bloquea;
- fallos parciales y fallback;
- service-to-service real entre servicios Vert.x;
- contraste contra `java-vertx-distributed`, donde la separación era por layer, no por bounded context.

### C. Monolith/modular baseline

`java-risk-engine` y `java-monolith` muestran que Clean Architecture no exige distribución física:

```text
domain/application/infrastructure/config/cmd
```

Esto sirve para demostrar:

- menor latencia;
- menor complejidad;
- boundaries por paquetes/tests;
- trade-off contra aislamiento operativo.



---

## 7.1 Validación externa: qué dice Vert.x y qué es decisión nuestra

Esta sección separa evidencia externa de decisión del repo.

### Respaldado por documentación oficial de Vert.x

| Punto | Evidencia | Fuente |
|---|---|---|
| Vert.x sirve para comunicar partes de una app o servicios distintos | El EventBus permite que partes de una aplicación, o aplicaciones/servicios distintos, se comuniquen de forma desacoplada. | [EventBus API](https://vertx.io/docs/apidocs/io/vertx/core/eventbus/EventBus.html) |
| EventBus soporta request-response, point-to-point y pub/sub | Esos tres patrones están documentados explícitamente. | [Vert.x Core — Event Bus](https://vertx.io/docs/vertx-core/java/) |
| EventBus puede ser distribuido entre nodos | Varias instancias Vert.x en red pueden formar un EventBus distribuido mediante clustering. | [Vert.x Core — Clustered Event Bus](https://vertx.io/docs/vertx-core/java/) |
| Service Proxy permite consumir servicios sobre EventBus con interfaz Java | Vert.x genera boilerplate y proxy cliente para invocar servicios sobre EventBus, incluso si viven en otra máquina. | [Vert.x Service Proxy](https://vertx.io/docs/vertx-service-proxy/java/) |
| Service Discovery reconoce servicios EventBus y HTTP | `EventBusService` representa service proxies; `HttpEndpoint` representa REST/HTTP endpoints. | [Vert.x Service Discovery](https://vertx.io/docs/vertx-service-discovery/java/) |
| Vert.x es event-driven/non-blocking y usa pocos threads | Sus APIs no bloqueantes permiten alta concurrencia con pocos threads. | [Vert.x Core — Don’t block me](https://vertx.io/docs/vertx-core/java/) |

### No lo prescribe Vert.x: es decisión arquitectónica del repo

La documentación de Vert.x **no dice** que controllers, usecases y repositories deban vivir juntos o separados. Eso pertenece a Clean Architecture, DDD y a la estrategia de despliegue del sistema.

Por lo tanto, en este repo la lectura correcta es:

```text
service-mesh-demo
  = experimento Vert.x-to-Vert.x service-to-service
  = separación por bounded context
  = verticles/EventBus como mecanismo técnico de comunicación
```

Eso no es una regla de Vert.x; es una decisión del PoC para evitar mezclar dos demostraciones:

1. **`java-vertx-distributed`**: separar layers técnicas en pods usando Vert.x clustered EventBus.
2. **`service-mesh-demo`**: separar bounded contexts dentro del stack Vert.x usando verticles/EventBus, sin afirmar que Vert.x obligue a una distribución específica de layers en pods.

Un `service-mesh-demo2` para `java-monolith` o `java-risk-engine` sería otra comparación, porque ya no estaría probando servicios/componentes Vert.x comunicándose por EventBus/proxies/cluster; estaría midiendo otro mecanismo inter-service, probablemente HTTP/gRPC/stdlib.

### D. Qué sería `service-mesh-demo2`

Un hipotético `service-mesh-demo2` para `java-monolith` o `java-risk-engine` sería otro experimento: comparar service-to-service con servicios que no comparten el stack Vert.x/EventBus. Eso podría servir para medir HTTP/gRPC/stdlib entre procesos, pero **no es el objetivo de `service-mesh-demo` actual**.

El objetivo actual es más específico:

```text
Vert.x component/service A (verticles + Clean Arch según decisión del PoC)
  -- EventBus RPC/async -->
Vert.x component/service B (verticles + Clean Arch según decisión del PoC)
```

Es decir: demostrar cómo Vert.x permite comunicar bounded contexts/componentes con el mismo stack reactivo mediante verticles/EventBus, sin prescribir una topología obligatoria de pods.

---

## 8. No crear un “communication service” central

La comunicación no debe ser un servicio aparte. Es una propiedad del stack elegido.

| PoC | Comunicación |
|---|---|
| `java-risk-engine` | In-process / stdlib HTTP adapter. |
| `java-monolith` | EventBus local dentro de una JVM. |
| `java-vertx-distributed` | Vert.x clustered EventBus. |
| `vertx-risk-platform` | HTTP entre pods con tokens. |
| `service-mesh-demo` | EventBus RPC/async entre servicios/componentes Vert.x; empaquetado por bounded context definido por el PoC. |

Un “communication-service” agregaría un hop, un SPOF lógico y una abstracción artificial. Para esta exploración es mejor que cada PoC exponga el costo real de su mecanismo.

---

## 9. Qué falta para que la comparación sea sólida

| Gap | Acción recomendada | Resultado |
|---|---|---|
| Benchmark comparable | Ejecutar `./nx bench k6 competition load` contra targets `bare`, `monolith`, `vertx-platform`, `distributed`. | Tabla p50/p95/p99/throughput/error-rate. |
| Contrato de request único | Alinear payload mínimo entre PoCs: transactionId, customerId, amount, newDevice, idempotencyKey, correlationId. | Misma carga funcional. |
| Misma lógica hot path | Extraer/usar `pkg:risk-domain` de forma consistente. | Comparación más justa. |
| Separar ML dominante | Bench con ML disabled/fixed latency y bench con ML simulated. | Ver overhead de arquitectura vs dependency externo. |
| Docs inconsistentes | Corregir referencias viejas a Java 25 como build real y “3 PoCs” donde ahora hay más variantes. | Narrativa confiable. |
| Vert.x benefits | Mantener esta matriz enlazada desde START-HERE/Quick Reference. | Reviewer entiende por qué Vert.x está en la exploración. |

---

## 10. Cómo contarlo en una discusión técnica

Respuesta corta:

> “No hice cinco apps porque sí. Mantengo el mismo problema de decisión de riesgo y cambio la topología: bare Java para latencia base, Vert.x monolith para event loop sin red, Vert.x EventBus clustered para layer-as-pod, HTTP pods para permisos/debuggability, y service-mesh-demo para bounded contexts reales dentro del stack Vert.x. Así puedo mostrar cuánto cuesta cada separación y cuándo vale la pena, sin afirmar que Vert.x imponga una topología de pods.”

Si preguntan por Vert.x:

> “Vert.x me sirve porque es event-driven, non-blocking, multi-reactor y trae EventBus local/distribuido. Entonces puedo demostrar el mismo diseño como monolito modular o como pods separados sin reescribir todo el modelo mental. La trampa es que si bloqueo el event loop, pierdo el beneficio; por eso el benchmark debe separar lógica CPU, I/O y latencia simulada de ML.”

Si preguntan si todos comparten lógica:

> “Comparten dominio conceptual y parte de librerías; todavía hay duplicación experimental. El plan correcto es consolidar reglas/DTOs/hot path en `pkg:risk-domain` y dejar que cambien solo los adapters/topologías. Eso haría la comparación de performance más justa.”
