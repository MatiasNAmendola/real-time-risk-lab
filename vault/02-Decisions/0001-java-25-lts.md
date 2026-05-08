---
adr: "0001"
title: Java 21 baseline operativo y Java 25 LTS objetivo
status: accepted
date: 2026-05-07
deciders: [Mati]
tags: [decision/accepted, area/runtime, area/java]
---

# ADR-0001: Java 21 baseline operativo y Java 25 LTS como objetivo

## Estado

Aceptado el 2026-05-07.

## Contexto

El use case target es una plataforma de riesgo transaccional en tiempo real sobre Java + EKS. Los PoCs de exploración deben usar una versión de Java defendible como target productivo en una conversación de diseño de 2026, que provea las features de lenguaje necesarias para demostrar idioms modernos de Java (virtual threads, pattern matching for switch, structured concurrency), y que esté disponible en la máquina de desarrollo (Apple M1 Pro vía Homebrew).

Al 2026-05-07, el timeline de releases de Java es: Java 21 LTS (septiembre 2023), Java 25 LTS (septiembre 2025), Java 26 (marzo 2026, feature release). Java 25 es el LTS actual.

JEPs clave que diferencian Java 25 de versiones LTS anteriores: Virtual Threads estables desde 21 y refinados; Scoped Values finalizados (JEP 481, Java 23, finalizado en 25); Structured Concurrency finalizada (JEP 505, Java 25); pattern matching for switch final desde 21, guarded patterns refinados hasta 25.

## Decisión

Usar **Java 21 LTS como compiler target operativo** (`--release 21`) para todos los módulos. Mantener **Java 25 LTS como objetivo arquitectónico documentado** para runtime moderno cuando el ecosistema de tooling soporte classfile 25 sin fricción. Java 26 no se usa como baseline: se prefiere estabilidad LTS.

## Alternativas consideradas

### Opción A: Java 21 LTS baseline operativo + Java 25 LTS objetivo (elegida)
- **Ventajas**: LTS actual — defendible como target productivo en cualquier conversación 2026; Virtual Threads, Structured Concurrency y Scoped Values todos finalizados; Gradle 8.11.1 y Vert.x 5 lo soportan; disponible en Apple M1 vía Homebrew; demuestra currency sin perseguir un feature release.
- **Desventajas**: No todos los equipos productivos migraron de 17 o 21; la JVM del benchmark resolvió a Temurin 21 en runtime (discrepancia entre compile target y runtime — documentada en doc 12); algunas imágenes Docker de CI son anteriores a Java 25.
- **Por qué se eligió**: La señal de diseño es conocimiento actualizado. "Targeteo Java 25 en greenfield; la migración desde 17 se hace en stages vía `--enable-preview`" es la respuesta correcta para un technical leadership engineer en 2026.

### Opción B: Java 21 LTS (LTS anterior)
- **Ventajas**: El LTS más desplegado en 2024-2025; máxima compatibilidad con Spring Boot 3.x; Virtual Threads estables; máximo soporte de tooling del ecosistema.
- **Desventajas**: Falta Structured Concurrency (finalizada en 25) y Scoped Values (finalizado en 25); señala "elección segura" en lugar de "conocimiento actualizado"; sin diferenciación respecto del Java developer promedio.
- **Por qué no**: Para conversaciones de diseño a nivel technical leadership en 2026, elegir el LTS anterior señala aversión al riesgo por sobre currency. Las features de Java 25 son los talking points.

### Opción C: Java 26 (feature release actual)
- **Ventajas**: Features más nuevas; máxima expresividad de lenguaje.
- **Desventajas**: No es LTS — los equipos productivos no adoptan feature releases para servicios; discutir Java 26 como target productivo es indefendible; compatibilidad del BOM de Vert.x 5 sin probar.
- **Por qué no**: Un technical leadership engineer distingue entre "feature release más reciente" y "LTS actual". Java 26 es apropiado para experimentación, no para un argumento de target productivo.

### Opción D: Java 17 LTS (estándar enterprise 2022-2024)
- **Ventajas**: El más usado en enterprise 2022-2025; máxima compatibilidad de ecosistema; soporte maduro de Spring Boot 3.x.
- **Desventajas**: Virtual Threads solo en preview (final en 21); pattern matching for switch no final; desactualizado para un servicio greenfield en 2026; señala conocimiento del pasado, no del presente.
- **Por qué no**: Usar Java 17 para desarrollo nuevo en 2026 requiere una justificación específica de compatibilidad. Sin ella, señala default thinking desactualizado.

## Consecuencias

### Positivo
- Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor()`) en `HttpController` no son `--enable-preview` — API estable.
- Structured Concurrency y Scoped Values son talking points finalizados para design reviews.
- `--release 21` evita fricción de classfile 25 en tooling y previene depender de features no disponibles en el baseline real.

### Negativo
- La JVM del benchmark resolvió a JDK 21.0.4 Temurin en runtime (doc 12) — el compile target y el runtime difieren. Esto es una discrepancia para explicar, no una elección de diseño.
- Ingenieros familiarizados primariamente con Java 17 pueden requerir explicación breve del delta de features.

### Mitigaciones
- Discrepancia del benchmark documentada en doc 12 con la versión exacta de la JVM.
- Respuesta tipo: "El benchmark corrió en 21 por accidente (resolución del PATH); el PoC compila a 25; Virtual Threads funcionan idéntico en ambos."

## Validación

- `./gradlew :pkg:resilience:build` usa el toolchain Java pin-eado en `build-logic/riskplatform.java-conventions.gradle.kts`.
- `HttpController.java` usa `Executors.newVirtualThreadPerTaskExecutor()` — sin necesidad de flag `--enable-preview`.

## Addendum 2026-05-08: target de bytecode bajado temporalmente a 21

Durante Phase 2 (migración a Gradle) descubrimos que varios tools de build fallan con `--release 21`:

- **JMH 1.37**: el annotation processor no produce `META-INF/BenchmarkList` con bytecode 25 — los benchmarks corren vacíos.
- **plugin `com.gradleup.shadow`** (fat jars Vert.x): `Class major version 69 not supported`.
- **Karate 1.4.x**: la reflection sobre sealed interfaces falla con bytecode 25.

Decisión pragmática: `--release 21` en los convention plugins. **El runtime no cambia** — la JVM 25 mantiene virtual threads, mejoras de GC, FFM API, structured concurrency. Solo el target de bytecode es 21.

Esto NO es una regresión de este ADR. Es un workaround temporal hasta que el ecosistema de build tools se ponga al día. Triggers para revertir documentados en `docs/26-java-version-compat-2026.md`.

Cuando JMH, Shadow, Karate y ArchUnit publiquen versiones compatibles con classfile 25, el cambio objetivo será una línea por convention plugin: `release.set(21)` → `release.set(25)`.

## Relacionado

- [[0037-virtual-threads-http-server]]
- [[0031-no-di-framework]]
- Docs: doc 04 (`docs/04-clean-architecture-java.md`)

## Referencias

- Release notes de Java 25: https://openjdk.org/projects/jdk/25/
- Virtual Threads JEP 444: https://openjdk.org/jeps/444
- Structured Concurrency JEP 505: https://openjdk.org/jeps/505
