---
title: Java moderno vs Go — performance, concurrencia y posicionamiento
tags: [concept, java, go, performance, runtime, gc, concurrency, technical-positioning]
created: 2026-05-12
source_archive: docs/37-java-go-performance-positioning.md (migrado 2026-05-12)
---

# Java moderno vs Go — performance, concurrencia y posicionamiento

## Tesis ejecutiva

Java moderno no "dejó de tener JVM", ni se volvió operacionalmente tan simple como Go. La afirmación defendible es más precisa:

> En servicios **long-running**, con tráfico sostenido y presupuesto de latencia estable, Java 21+ cerró gran parte de la brecha histórica con Go gracias a JIT/adaptive optimization, GCs de baja latencia, virtual threads, mejor ergonomía en containers y tooling de observabilidad. Go sigue siendo más simple en startup, binario único y memoria base; Java puede competir o ganar en throughput caliente, profiling y ecosistema enterprise.

Para este repo la conclusión práctica es: **Java 21 LTS es una elección razonable para el motor de riesgo** porque el workload es un servicio caliente, con 150 TPS sostenidos y p99 < 300ms, donde importan throughput, tail latency, observabilidad y límites limpios más que startup instantáneo.

---

## No afirmar de más

Evitar estas frases:

- "Java es más rápido que Go".
- "Go ya no tiene ventajas".
- "Virtual threads son iguales a goroutines".
- "Java 25 es el baseline real del repo".

Usar esta formulación:

> Go gana por simplicidad operacional, binario único, startup rápido y bajo footprint base. Java moderno gana terreno en servicios calientes por JIT, GC configurable, virtual threads y tooling. En este caso, el baseline ejecutable es Java 21 LTS; Java 25 queda como objetivo documentado cuando el tooling lo permita.

---

## Qué cambió en Java durante los últimos años

### 1. JIT/adaptive optimization: ventaja en workloads calientes

HotSpot usa interpretación, profiling y compilación JIT para optimizar las partes calientes del programa.

> Si el servicio vive minutos u horas y recibe tráfico sostenido, el costo de warm-up se amortiza. Ahí Java puede recuperar o superar throughput porque el JIT optimiza el camino caliente real.

Fuente: [Oracle JVM Technology Overview](https://docs.oracle.com/en/java/javase/11/vm/java-virtual-machine-technology-overview.html)

### 2. GCs de baja latencia: ZGC y G1 reducen el viejo miedo a pausas largas

ZGC fue diseñado para baja latencia y heap grande. JEP 439 (Generational ZGC en JDK 21) describe pausas típicamente menores a 1 ms.

Para este repo:
- Demo/local: G1 default es suficiente y predecible.
- Deep dive de latencia: documentar opción ZGC/Generational ZGC en JDK 21+.

Fuentes: [JEP 376](https://openjdk.org/jeps/376) y [JEP 439](https://openjdk.org/jeps/439).

### 3. Virtual threads: Java recuperó simplicidad para concurrencia blocking-style

JEP 444 finalizó virtual threads en Java 21. Su objetivo explícito es permitir aplicaciones server thread-per-request con alta utilización de hardware.

**Comparación honesta:**

- Go: goroutines son parte idiomática del lenguaje desde el inicio.
- Java: virtual threads llegan sobre una plataforma enterprise existente; son muy útiles para I/O/blocking code, pero requieren entender pinning, pools y librerías usadas.

Fuente: [JEP 444](https://openjdk.org/jeps/444).

### 4. Container awareness: la JVM entiende mejor límites reales

La JVM de JDK 21 describe opciones como `MaxRAMPercentage` y considera restricciones de containers. Java en Docker/Kubernetes dejó de ser tan peligroso por ergonomics erradas de heap/CPU.

### 5. Startup/footprint: Java mejoró, pero Go sigue ganando por defecto

Go sigue siendo más directo: binario único, startup rápido y menor memoria base sin tuning especial. Java tiene AppCDS, jlink y GraalVM Native Image, pero no conviene venderlo como default "gratis".

---

## Qué Go sigue haciendo muy bien

### Goroutines y scheduler integrado

Goroutines son livianas, con stacks chicos que crecen según necesidad, multiplexadas sobre OS threads. Fuente: [Effective Go](https://go.dev/doc/effective_go).

### GC concurrente con foco en latencia

El GC estándar de Go hace la mayor parte del trabajo concurrentemente para reducir latencias. Fuente: [A Guide to the Go Garbage Collector](https://go.dev/doc/gc-guide).

### Containers: Go también siguió mejorando

Go 1.25 incorporó defaults de `GOMAXPROCS` conscientes de containers. Fuente: [Go 1.25 release notes](https://go.dev/doc/go1.25).

---

## Matriz honesta Java moderno vs Go

| Dimensión | Go | Java 21+ moderno | Lectura para Risk Platform |
|---|---|---|---|
| Startup | Gana por defecto | Mejoró con CDS/jlink/GraalVM, pero no gratis | No es crítico: servicio long-running |
| Memoria base | Gana por defecto | Mayor footprint; requiere límites/tuning | Aceptable si se modela CPU/RAM por suite/pod |
| Throughput caliente | Muy competitivo | Muy competitivo por JIT/adaptive optimization | Java es defendible para 150 TPS sostenidos |
| Tail latency | Buen GC + scheduler simple | G1/ZGC + tuning + observabilidad | Medir p99/p99.9, no asumir |
| Concurrencia blocking | Goroutines idiomáticas | Virtual threads desde Java 21 | Java cerró brecha para thread-per-request |
| Operación | Toolchain simple, binario único | JVM + flags + warm-up + classpath | Más complejo, pero observable y tunable |
| Profiling | pprof/trace excelentes | JFR/async-profiler/JMX ecosistema maduro | Java fuerte para diagnóstico enterprise |
| Ecosistema enterprise | Bueno y simple | Muy amplio, estable, integrable | Java encaja con bancos/fraude/compliance |

---

## Cómo responder en discusión técnica

### "¿Por qué Java y no Go para un motor de riesgo?"

> Go habría sido una opción excelente si priorizara binario único, startup y simplicidad operacional. Elegí Java porque el caso es un servicio long-running, con tráfico sostenido y mucho peso en observabilidad, contratos, librerías enterprise y performance caliente. Java 21 me da JIT maduro, GCs de baja latencia, virtual threads si quiero un modelo blocking y Vert.x si quiero event-loop. La decisión no es "Java es más rápido que Go", sino "para este contexto, sus trade-offs son defendibles".

### "¿Java no consume demasiada memoria?"

> Sí, la JVM tiene mayor memoria base que Go. Por eso este repo modela límites explícitos de CPU/RAM, evita suites que arranquen muchas JVMs sin control y separa quick/ci-full/k8s. En producción haría sizing por pod, `MaxRAMPercentage`, heap explícito y mediciones con JFR/OTel.

### "¿Virtual threads reemplazan a Vert.x?"

> No necesariamente. Virtual threads son muy buenos para código blocking-style y thread-per-request. Vert.x sigue siendo excelente para event-loop, backpressure y alto throughput I/O. En este repo conviven como discusión de trade-off, no como dogma.

---

## Cierre

La comparación honesta no es "Java vs Go" como religión. Es:

- **Go**: simplicidad, arranque, binario único, memoria base baja, concurrencia idiomática.
- **Java moderno**: throughput caliente, JIT, GC configurable, virtual threads, observabilidad y ecosistema enterprise.

Para un motor de fraude/riesgo que vive caliente, tiene SLO de latencia y necesita trazabilidad fuerte, **Java 21+ es defendible**. Para CLIs, herramientas chicas o servicios ultra simples, **Go sigue siendo una opción excelente**.

## Related

- [[0047-go-version-policy]] — política de versión de Go para el CLI de smoke.
- [[0035-java-go-polyglot]] — decisión de adoptar Go para el CLI.
- [[Virtual-Threads-Loom]] — virtual threads en Java.
- [[Latency-Budget]] — presupuesto de latencia que contextualiza la elección.
- [[Risk-Platform-Overview]]
