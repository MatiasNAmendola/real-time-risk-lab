---
adr: "0037"
title: Virtual Threads para el HTTP Server Executor en la PoC bare-javac
status: accepted
date: 2026-05-07
deciders: [Mati]
tags: [decision/accepted, area/runtime, area/java, area/concurrency]
---

# ADR-0037: Virtual Threads (Java 21+) como executor del HTTP Server

## Estado

Aceptado el 2026-05-07.

## Contexto

La PoC bare-javac suma un servidor HTTP (`HttpController`) usando `com.sun.net.httpserver.HttpServer`, el server HTTP integrado al JDK desde Java 6. `HttpServer` acepta un `Executor` que maneja los requests entrantes. El executor por defecto despacha sobre un thread pool interno.

La decisión es qué executor proveer. Las opciones: el default (thread pool interno del JDK), un thread pool de plataforma de tamaño fijo, un thread pool cacheado, o un executor virtual-thread-per-task (Java 21+ estable).

Esta es una feature concreta y demostrable de Java moderno (disponible desde Java 21) que conviene poner al frente: los virtual threads son la primitiva de concurrencia nueva más relevante de Java moderno, y mostrarlos en un servidor HTTP funcionando demuestra entendimiento práctico, no solo familiaridad con la JEP.

## Decisión

Se pasa `Executors.newVirtualThreadPerTaskExecutor()` como executor a `HttpServer.setExecutor()`. Cada request entrante corre en un virtual thread nuevo. El `OutboxRelay` también usa `Executors.newVirtualThreadPerTaskExecutor()` para su loop de relay.

## Alternativas consideradas

### Opción A: executor virtual-thread-per-task (elegida)
- **Ventajas**: cada request obtiene su propio virtual thread, sin necesidad de dimensionar thread pool; el I/O bloqueante (llamadas a base, invocación al scorer) no bloquea threads de plataforma; escala a alta concurrencia sin tuning; cambio de una línea desde el default; artefacto reviewable: `Executors.newVirtualThreadPerTaskExecutor()` queda visible y discutible al instante; la memoria por virtual thread es del orden de kilobytes contra megabytes de los threads de plataforma.
- **Desventajas**: los virtual threads no son adecuados para trabajo CPU-bound (siguen pinneando carrier threads en loops intensivos de CPU); las thread-locals se comportan distinto (pinning); los bloques `synchronized` pinnean el virtual thread al carrier —se prefiere `ReentrantLock`.
- **Por qué se eligió**: el handler HTTP en la PoC bare-javac es I/O-bound (llamada al scorer con latencia de 20-160ms). Los virtual threads son el executor óptimo para este workload. El cambio de una sola línea es la feature de Java moderno más visible en el codebase.

### Opción B: thread pool de plataforma fijo (`Executors.newFixedThreadPool(N)`)
- **Ventajas**: uso predecible de recursos; fácil de dimensionar (N = 2x CPU cores para I/O-bound); patrón Java estándar.
- **Desventajas**: requiere tuning —un N equivocado causa o desperdicio de recursos o queuing; el I/O bloqueante bloquea el thread de plataforma; escalar exige cambiar N y redeployar; no demuestra features modernas de Java.
- **Por qué no**: el sizing de thread pool es justamente el problema que resuelven los virtual threads. Usar un pool fijo en una PoC que demuestra Java moderno pierde el punto de diseño principal.

### Opción C: thread pool de plataforma cacheado (`Executors.newCachedThreadPool()`)
- **Ventajas**: unbounded, sin queuing de requests; los threads se reutilizan cuando hay disponibles.
- **Desventajas**: creación unbounded de threads bajo carga —a 150 TPS con scorer de 160ms, 24 requests concurrentes son al menos 24 threads de plataforma; cada thread consume aproximadamente 1MB; riesgo de OOM ante picos; no demuestra virtual threads.
- **Por qué no**: la creación unbounded de threads de plataforma es el problema que los virtual threads resuelven con más elegancia.

### Opción D: patrón reactor (event loop non-blocking estilo Netty)
- **Ventajas**: throughput máximo; usado por Vert.x, Netty y WebFlux; eficiente para concurrencia muy alta.
- **Desventajas**: requiere modelo reactivo —callbacks o reactive streams; callback-hell o cadenas Flux/Mono; mucho más complejo que virtual threads para el mismo workload I/O-bound; rompe el objetivo "simple, legible" de la PoC bare-javac.
- **Por qué no**: para workloads I/O-bound con concurrencia moderada (150 TPS), los virtual threads ofrecen performance equivalente con código drásticamente más simple. El patrón reactor aplica cuando hace falta manejar millones de conexiones concurrentes, no 150 TPS.

## Consecuencias

### Positivo
- `HttpController` sirve 1528 req/s (BenchmarkRunner, 32 virtual threads), bien por encima del requerimiento de 150 TPS.
- Sin tuning de thread pool: `newVirtualThreadPerTaskExecutor()` escala automáticamente.
- El lifecycle de cada request es una llamada secuencial simple: legible, debuggable, sin cadenas de callbacks.
- `OutboxRelay` usa el mismo executor; el relay corre concurrentemente sin gestión separada de threads.

### Negativo
- Los virtual threads pinnean al carrier durante bloques `synchronized`; `CircuitBreaker` usa métodos `synchronized`, lo que pinnea el virtual thread durante los chequeos de estado. Para un circuit breaker de baja contención es aceptable; a muy alta concurrencia se preferiría `ReentrantLock`.
- Las thread-locals usadas por frameworks de logging (SLF4J MDC) se comportan correctamente en virtual threads, pero hay que tener presente que `InheritableThreadLocal` no se propaga a los hijos por defecto.

### Mitigaciones
- `CircuitBreaker` usa `synchronized` —aceptable para la PoC dada la baja contención. En producción: migrar a `ReentrantLock` o `StampedLock`.
- `BenchmarkRunner` mide throughput con 32 virtual threads: 1528 req/s a p99=153ms, validando empíricamente la elección.

## Validación

- `HttpController.java`: `this.server.setExecutor(Executors.newVirtualThreadPerTaskExecutor())`.
- `RiskApplicationFactory.java`: `new OutboxRelay(outboxRepository, Executors.newVirtualThreadPerTaskExecutor())`.
- `BenchmarkRunner` con 32 virtual threads alcanza unos 1528 req/s (medidos, doc 12).
- `java.lang.Thread.isVirtual()` devuelve `true` en el thread del request handler durante el smoke test.

## Relacionado

- [[0001-java-25-lts]]
- [[0031-no-di-framework]]
- [[0023-smoke-runner-asymmetric]]
- Docs: doc 12 (`docs/12-rendimiento-y-separacion.md`)

## Referencias

- Virtual Threads JEP 444: https://openjdk.org/jeps/444
- JDK HttpServer: https://docs.oracle.com/en/java/docs/books/tutorial/networking/
