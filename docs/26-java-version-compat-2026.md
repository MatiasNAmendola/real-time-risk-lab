# 26 — Java version compatibility 2026: por qué `release 21` aunque ADR-0001 dice 25 LTS

## TL;DR

ADR-0001 establece **Java 25 LTS** como decisión canónica. La realidad operativa a 2026-05-08 fuerza un compromiso pragmático: **compilamos con `--release 21`, ejecutamos en cualquier JDK 21+ (incluyendo 25)**. Esto NO es regresión de la decisión arquitectónica — es un workaround temporal hasta que el ecosistema de tooling de build catches up.

## Lo que pasó

Phase 2 (migración a Gradle 8 con Kotlin DSL) intentó honrar Java 25 con `<release>25</release>` en convention plugins. Hit **cuatro paredes** distintas en la cadena de dependencias:

### 1. JMH 1.37 annotation processor

El `bench/inprocess-bench/` usa JMH (Java Microbenchmark Harness) para medir el use case. JMH genera código en compile time vía un annotation processor que produce `META-INF/BenchmarkList` con la lista de benchmarks descubiertos.

Con `--release 25`:
- El processor ejecuta sin errores.
- Genera classes correctas.
- **Pero no escribe `META-INF/BenchmarkList`** porque el classfile reader interno del processor no entiende los class file attributes nuevos (sealed types, value types preview).

Síntoma: `java -jar benchmarks.jar` imprime `No matching benchmarks. Miss-spelled regexp?` y termina.

Fix: `--release 21` en `bench/inprocess-bench/build.gradle.kts`.

### 2. Shadow plugin para fat jars Vert.x

Cada app Vert.x (`controller-app`, `usecase-app`, `repository-app`, `consumer-app`) necesita un fat jar (uberjar) con todas las deps inlined. El plugin canónico es `com.gradleup.shadow:8.x` (heredero de `com.github.johnrengelman.shadow`).

Con `--release 25`:
- El plugin lee class files de las deps.
- Encuentra entries en el constant pool con tags que su parser no reconoce.
- Falla con `Unknown CONSTANT_pool tag: ...` o `Class major version 69 not supported`.

Fix: `--release 21` en convention plugin app.

Workaround alternativo evaluado: Gradle `application` plugin + distribution zip (sin fat jar). Funciona pero el Dockerfile de cada Vert.x app espera un fat jar — refactor más grande.

### 3. Karate 1.4.x con sealed interfaces

El rules engine (`pkg/risk-domain`) usa `sealed interface FraudRule permits HighAmountRule, NewDeviceYoungCustomerRule, ...`. Karate corre tests via reflection — cuando carga las classes para introspección, Karate 1.4.x falla con `IllegalAccessException` sobre los `permits` clauses si el class file está en bytecode 25.

Karate 1.5.0 no existe (en mi prompt original al implementador estaba mal — el agente correctamente cayó a 1.4.1).

Fix: bytecode 21 — los sealed interfaces existen igual (Java 17+), simplemente con classfile attributes que Karate sí entiende.

### 4. build-helper-maven-plugin y otras herramientas

Hay un par de scripts y plugins legacy (algunos de los `pom.maven-legacy.xml` mantenidos para reverse) que esperan bytecode <= 21. No es bloqueante porque ya no son la build canónica, pero documenta la fricción.

## Decisión

Convention plugin `naranja.java-conventions.gradle.kts`:

```kotlin
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}
```

JDK toolchain en máquina del developer puede ser cualquier 21+. Recomendado JDK 25 para aprovechar runtime features sin afectar bytecode target.

## Lo que ganamos en runtime con JDK 25 (sin tocar bytecode)

Aunque compilamos para 21, si corrés con JDK 25:
- **Virtual threads** (Project Loom, JEP 444) — disponible desde 21 pero mejorado en 25.
- **Generational ZGC** mejorado.
- **JIT mejoras** (hot path inlining, escape analysis).
- **Foreign Function & Memory API** mature.

Lo que **NO** podés usar:
- Pattern matching extendido (Java 23+ syntax).
- Primitive types in generics (si saliera).
- String templates si quedaran finalizados en 25.

## Cuándo subir a `--release 25`

Disparadores:
1. JMH publica versión que soporta classfile 25 (issue tracker: https://github.com/openjdk/jmh/issues — verificar).
2. Shadow plugin publica versión que soporta classfile 25.
3. Karate publica versión con reflection compatible.

Cuando los 3 se den, bumpeamos `release` a 25 en convention plugins, corremos `./gradlew clean build`, sale verde, listo. Es un cambio de 1 línea por convention plugin.

## How to Defend This Decision

Pregunta probable: "Si decidiste Java 25 LTS, ¿por qué tu pom dice 21?"

Respuesta:

> "Porque en mayo 2026 el ecosistema Gradle todavía no catched up. JMH 1.37, Shadow plugin estable y Karate 1.4 fallan con bytecode 25. La decisión arquitectónica sigue siendo Java 25 — el workaround es compilar a 21 hasta que las tres herramientas publiquen versiones compatibles. Es exactamente el tipo de tradeoff que diferencia ingeniería pragmática de purismo. Cuando el ecosistema esté listo, es un cambio de una línea."

Pregunta de seguimiento: "¿Y por qué no usás Java 21 LTS directamente entonces, si te conformás con 21 en bytecode?"

Respuesta:

> "Porque las decisiones de **runtime** sí son Java 25: virtual threads escaling, GC mejoras, FFM API. El JVM en producción corre 25 LTS. Solo el target del compiler es 21 hasta que las tools soporten 25. Si bajara la decisión completa a 21 LTS, sería renunciar a las features runtime que aprovechamos hoy."

## Key Design Principle

> "Cuando la decisión arquitectónica y la realidad del tooling no coinciden, ganan los dos: documentás la decisión, anotás el workaround, marcás los disparadores para revertir. Lo que no podés hacer es esconder la fricción — eso es deuda técnica invisible."

## Anti-patterns evitados

- **Forzar release 25 con `--add-opens` y workarounds de classpath**: el bytecode quedaría inestable. NO.
- **Bajar la ADR a Java 21 LTS**: sería renunciar a runtime features. NO.
- **Esperar al ecosistema antes de seguir trabajando**: parálisis por purismo. NO.
- **No documentar la deviation**: pretender que todo está bien. PEOR de los anti-patterns.

## Status disparadores (chequear periódicamente)

| Tool | Versión actual | Soporta classfile 25 | Acción |
|---|---|---|---|
| JMH | 1.37 | ✗ | Watch https://openjdk.org/projects/code-tools/jmh/ |
| Shadow plugin | 8.x | ✗ | Watch https://github.com/GradleUp/shadow/releases |
| Karate | 1.4.1 | ✗ (sealed types) | Watch https://github.com/karatelabs/karate/releases |
| build-helper-maven-plugin | 3.x | parcial | No bloqueante |

Cuando los 3 primeros estén ✓, abrir un PR de 1 línea para subir el target.
