---
title: "37 — Java moderno vs Go: performance, concurrencia y posicionamiento"
tags: [java, go, performance, runtime, gc, concurrency, technical-positioning]
---

# 37 — Java moderno vs Go: performance, concurrencia y posicionamiento

## Tesis ejecutiva

Java moderno no “dejó de tener JVM”, ni se volvió operacionalmente tan simple como Go. La afirmación defendible es más precisa:

> En servicios **long-running**, con tráfico sostenido y presupuesto de latencia estable, Java 21+ cerró gran parte de la brecha histórica con Go gracias a JIT/adaptive optimization, GCs de baja latencia, virtual threads, mejor ergonomía en containers y tooling de observabilidad. Go sigue siendo más simple en startup, binario único y memoria base; Java puede competir o ganar en throughput caliente, profiling y ecosistema enterprise.

Para este repo la conclusión práctica es: **Java 21 LTS es una elección razonable para el motor de riesgo** porque el workload es un servicio caliente, con 150 TPS sostenidos y p99 < 300ms, donde importan throughput, tail latency, observabilidad y límites limpios más que startup instantáneo.

---

## No afirmar de más

Evitar estas frases:

- “Java es más rápido que Go”.
- “Go ya no tiene ventajas”.
- “Virtual threads son iguales a goroutines”.
- “Java 25 es el baseline real del repo”.

Usar esta formulación:

> Go gana por simplicidad operacional, binario único, startup rápido y bajo footprint base. Java moderno gana terreno en servicios calientes por JIT, GC configurable, virtual threads y tooling. En este caso, el baseline ejecutable es Java 21 LTS; Java 25 queda como objetivo documentado cuando el tooling lo permita.

---

## Qué cambió en Java durante los últimos años

### 1. JIT/adaptive optimization: ventaja en workloads calientes

HotSpot no ejecuta todo el bytecode de la misma manera para siempre. Usa interpretación, profiling y compilación JIT para optimizar las partes calientes del programa. Oracle documenta que HotSpot usa un compilador adaptativo para decidir cómo optimizar código compilado, incluyendo técnicas como inlining.

**Implicancia:** en servicios long-running, el runtime puede optimizar según el comportamiento real de producción. Go compila ahead-of-time y arranca simple, pero no hace el mismo tipo de optimización dinámica con perfil caliente.

**Cómo explicarlo en una discusión técnica:**

> Si el servicio vive minutos u horas y recibe tráfico sostenido, el costo de warm-up se amortiza. Ahí Java puede recuperar o superar throughput porque el JIT optimiza el camino caliente real.

Fuente primaria: [Oracle JVM Technology Overview](https://docs.oracle.com/en/java/javase/11/vm/java-virtual-machine-technology-overview.html) y [HotSpot performance enhancements](https://docs.oracle.com/en/java/javase/11/vm/java-hotspot-virtual-machine-performance-enhancements.html).

### 2. GCs de baja latencia: ZGC y G1 reducen el viejo miedo a pausas largas

ZGC fue diseñado para baja latencia y heap grande. JEP 376 movió procesamiento de stacks fuera de safepoints hacia una fase concurrente. JEP 439 agregó Generational ZGC en JDK 21 para mejorar performance al separar generaciones joven/vieja; el propio JEP describe pausas típicamente menores a 1 ms en ZGC.

**Implicancia:** Java ya no debe defenderse solo con “el GC es suficientemente bueno”. Hoy puede elegir G1 por defecto operacional o ZGC cuando el objetivo principal sea cola de latencia/pause-time bajo.

**Para este repo:**

- Demo/local: G1 default es suficiente y predecible.
- Deep dive de latencia: documentar opción ZGC/Generational ZGC en JDK 21+.
- No cambiar flags globales sin medir con los benchmarks del repo.

Fuentes primarias: [JEP 376](https://openjdk.org/jeps/376) y [JEP 439](https://openjdk.org/jeps/439).

### 3. Virtual threads: Java recuperó simplicidad para concurrencia blocking-style

JEP 444 finalizó virtual threads en Java 21. Su objetivo explícito es permitir aplicaciones server thread-per-request con alta utilización de hardware y migración mínima desde código basado en `java.lang.Thread`.

**Implicancia:** Java redujo una ventaja histórica de Go: escribir código concurrente que parece secuencial sin pagar un OS thread por request.

**Comparación honesta:**

- Go: goroutines son parte idiomática del lenguaje desde el inicio.
- Java: virtual threads llegan sobre una plataforma enterprise existente; son muy útiles para I/O/blocking code, pero requieren entender pinning, pools y librerías usadas.

**Para este repo:**

- `poc/java-risk-engine` puede mostrar virtual threads como modo de benchmark/concurrencia.
- En Vert.x, el modelo principal sigue siendo event-loop; no mezclar virtual threads en event-loop sin una razón explícita.

Fuente primaria: [JEP 444](https://openjdk.org/jeps/444).

### 4. Container awareness: la JVM entiende mejor límites reales

La documentación del comando `java` de JDK 21 describe opciones como `MaxRAMPercentage` y explica que el máximo disponible para la JVM considera memoria física y restricciones del entorno, por ejemplo containers.

**Implicancia:** Java en Docker/Kubernetes dejó de ser tan peligroso por ergonomics erradas de heap/CPU como en generaciones anteriores, aunque sigue requiriendo más criterio que Go.

**Para este repo:**

- Mantener flags explícitos en compose/docker-compose.yml para evitar que una JVM local consuma de más.
- Para demos multi-JVM, preferir `-XX:ActiveProcessorCount` y límites de memoria claros.

Fuente primaria: [JDK 21 `java` command](https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html).

### 5. Startup/footprint: Java mejoró, pero Go sigue ganando por defecto

AppCDS busca mejorar startup y footprint compartiendo metadata/clases; JEP 310 documenta ese objetivo. `jlink` permite armar runtimes custom por módulos. GraalVM Native Image puede reducir startup y memoria, aunque cambia el trade-off de build, reflexión, compatibilidad y peak performance.

**Implicancia:** Java tiene herramientas para achicar el gap de startup/footprint, pero no conviene venderlo como default “gratis”. Go sigue siendo más directo: binario único, startup rápido y menor memoria base sin tuning especial.

Fuentes primarias: [JEP 310 AppCDS](https://openjdk.org/jeps/310), [JDK 21 jlink module](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.jlink/module-summary.html), [GraalVM Native Image docs](https://www.graalvm.org/latest/reference-manual/native-image/).

---

## Qué Go sigue haciendo muy bien

### Goroutines y scheduler integrado

Effective Go describe goroutines como livianas, con stacks chicos que crecen según necesidad, multiplexadas sobre OS threads. Esto hace que el modelo concurrente de Go sea simple y barato desde el lenguaje.

Fuente primaria: [Effective Go — Goroutines](https://go.dev/doc/effective_go).

### GC concurrente con foco en latencia

La guía oficial del GC de Go explica que el GC estándar no es fully stop-the-world y hace la mayor parte del trabajo concurrentemente para reducir latencias de aplicación. También reconoce el trade-off: menor latencia puede implicar costos de throughput y CPU según allocation rate/tuning.

Fuente primaria: [A Guide to the Go Garbage Collector](https://go.dev/doc/gc-guide).

### Containers: Go también siguió mejorando

Go 1.25 incorporó defaults de `GOMAXPROCS` conscientes de containers para evitar throttling que impacta tail latency. Esto refuerza que Go también evoluciona en el mismo terreno operacional; no es una foto estática.

Fuente primaria: [Go blog — Container-aware GOMAXPROCS](https://go.dev/blog/container-aware-gomaxprocs) y [Go 1.25 release notes](https://go.dev/doc/go1.25).

---

## Matriz honesta Java moderno vs Go

| Dimensión | Go | Java 21+ moderno | Lectura para Risk Platform |
|---|---|---|---|
| Startup | Gana por defecto | Mejoró con CDS/jlink/GraalVM, pero no gratis | No es crítico: servicio long-running |
| Memoria base | Gana por defecto | Mayor footprint; requiere límites/tuning | Aceptable si se modela CPU/RAM por suite/pod |
| Throughput caliente | Muy competitivo | Muy competitivo por JIT/adaptive optimization | Java es defendible para 150 TPS sostenidos |
| Tail latency | Buen GC + scheduler simple | G1/ZGC + tuning + observabilidad | Medir p99/p99.9, no asumir |
| Concurrencia blocking | Goroutines idiomáticas | Virtual threads desde Java 21 | Java cerró brecha para thread-per-request |
| Event-loop/reactivo | Disponible pero menos central | Vert.x/Netty muy maduros | Vert.x es buena elección para PoCs HTTP/event bus |
| Operación | Toolchain simple, binario único | JVM + flags + warm-up + classpath | Más complejo, pero observable y tunable |
| Profiling | pprof/trace excelentes | JFR/async-profiler/JMX ecosistema maduro | Java fuerte para diagnóstico enterprise |
| Ecosistema enterprise | Bueno y simple | Muy amplio, estable, integrable | Java encaja con bancos/fraude/compliance |

---

## Cómo responder en discusión técnica

### Pregunta: “¿Por qué Java y no Go para un motor de riesgo?”

Respuesta corta:

> Go habría sido una opción excelente si priorizara binario único, startup y simplicidad operacional. Elegí Java porque el caso es un servicio long-running, con tráfico sostenido y mucho peso en observabilidad, contratos, librerías enterprise y performance caliente. Java 21 me da JIT maduro, GCs de baja latencia, virtual threads si quiero un modelo blocking y Vert.x si quiero event-loop. La decisión no es “Java es más rápido que Go”, sino “para este contexto, sus trade-offs son defendibles”.

### Pregunta: “¿Java no consume demasiada memoria?”

> Sí, la JVM tiene mayor memoria base que Go. Por eso este repo modela límites explícitos de CPU/RAM, evita suites que arranquen muchas JVMs sin control y separa quick/ci-full/k8s. En producción haría sizing por pod, `MaxRAMPercentage`, heap explícito y mediciones con JFR/OTel.

### Pregunta: “¿Virtual threads reemplazan a Vert.x?”

> No necesariamente. Virtual threads son muy buenos para código blocking-style y thread-per-request. Vert.x sigue siendo excelente para event-loop, backpressure y alto throughput I/O. En este repo conviven como discusión de trade-off, no como dogma.

---

## Aplicación concreta al repo

### Baseline de lenguaje

- Build real: **Java 21 LTS** (`--release 21`).
- Objetivo documentado: **Java 25 LTS** cuando Gradle/JMH/Karate/ArchUnit y classfile 25 no generen fricción.
- No afirmar Java 25 como baseline ejecutable actual.

Ver: [`docs/26-java-version-compat-2026.md`](26-java-version-compat-2026.md).

### Qué medir

Para defender performance, priorizar evidencia local:

```bash
./nx bench inproc
./nx test --composite quick
./nx test --composite ci-fast
./nx bench k6 smoke --target distributed   # requiere k6 + compose
```

### Qué no medir con conclusiones fuertes

- Startup de Gradle como proxy de startup de aplicación.
- Corridas con múltiples `./gradlew` simultáneos como proxy de performance Java.
- Microbenchmarks sin JMH/warm-up.
- p99 local con la máquina saturada por Docker/OrbStack/varios daemons.

### Decisión operacional para tests

El hallazgo de esta investigación refuerza la taxonomía actual del runner:

- `quick`: una señal barata, no una tormenta de JVMs.
- `ci-fast`: unit + arch + SDK sin infra pesada.
- `ci-full`: explícitamente caro, con compose/Testcontainers/contract/k6.
- `k8s`: nightly/deep dive.

---

## Fuentes primarias

### Java / OpenJDK / Oracle

- [JEP 444 — Virtual Threads](https://openjdk.org/jeps/444)
- [JEP 439 — Generational ZGC](https://openjdk.org/jeps/439)
- [JEP 376 — ZGC: Concurrent Thread-Stack Processing](https://openjdk.org/jeps/376)
- [JEP 310 — Application Class-Data Sharing](https://openjdk.org/jeps/310)
- [Oracle JVM Technology Overview](https://docs.oracle.com/en/java/javase/11/vm/java-virtual-machine-technology-overview.html)
- [Oracle HotSpot VM Performance Enhancements](https://docs.oracle.com/en/java/javase/11/vm/java-hotspot-virtual-machine-performance-enhancements.html)
- [JDK 21 `java` command](https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html)
- [JDK 21 `jdk.jlink` module](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.jlink/module-summary.html)
- [GraalVM Native Image documentation](https://www.graalvm.org/latest/reference-manual/native-image/)

### Go

- [Effective Go — Goroutines](https://go.dev/doc/effective_go)
- [A Guide to the Go Garbage Collector](https://go.dev/doc/gc-guide)
- [Go blog — Container-aware GOMAXPROCS](https://go.dev/blog/container-aware-gomaxprocs)
- [Go 1.25 Release Notes](https://go.dev/doc/go1.25)
- [Go 1.14 Release Notes — async goroutine preemption](https://go.dev/doc/go1.14)

---

## Cierre

La comparación honesta no es “Java vs Go” como religión. Es:

- **Go**: simplicidad, arranque, binario único, memoria base baja, concurrencia idiomática.
- **Java moderno**: throughput caliente, JIT, GC configurable, virtual threads, observabilidad y ecosistema enterprise.

Para un motor de fraude/riesgo que vive caliente, tiene SLO de latencia y necesita trazabilidad fuerte, **Java 21+ es defendible**. Para CLIs, herramientas chicas o servicios ultra simples, **Go sigue siendo una opción excelente**.
