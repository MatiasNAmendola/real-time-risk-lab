# Construcción del monorepo

## Prerrequisitos

- **Java 21+** en el PATH (Temurin recomendado). El target de bytecode es `--release 21`; el runtime puede ser JDK 21, 25 o superior.
- El resolver Foojay aprovisiona automáticamente un toolchain Java 21 si no está instalado.
- No se requiere instalación global de Gradle — el wrapper descarga Gradle 8.11.1 en la primera corrida.

> **¿Por qué bytecode 21 y no 25?** La decisión arquitectónica (ADR-0001) documenta Java 25 LTS como objetivo, pero JMH 1.37, el plugin Shadow y Karate 1.4 fallan con classfile 25 en 2026-05. Compromiso pragmático: bytecode 21; runtime JDK 21+ (Java 25 opcional como objetivo). Ver `vault/02-Decisions/0001-java-25-lts.md` para detalles completos + triggers para revertir.

## Primera corrida

La primera invocación descarga Gradle 8.11.1 (~130 MB) en `~/.gradle/wrapper/dists/`:

```
./gradlew build
```

Las corridas siguientes usan la cache local y son significativamente más rápidas gracias al configuration cache.

## Cómo funciona el reactor Gradle

Este monorepo usa dos builds Gradle compuestos en conjunto:

- **build-logic/** — un composite build que define los convention plugins (`riskplatform.*-conventions`).
  Se resuelve antes del build raíz para que los plugins estén disponibles durante la configuración.
- **Build raíz** — el reactor principal que incluye todos los subproyectos `pkg:*`, `sdks:*`, `poc:*`, `tests:*` y `bench:*`.

Los subproyectos declaran solo un convention plugin en su `build.gradle.kts`;
todo el toolchain Java (Java 21, `--release 21`), encoding, test runner y configuración de JaCoCo
se hereda:

| Plugin | Para qué |
|---|---|
| `riskplatform.library-conventions` | librerías compartidas `pkg/*` y `sdks/*` |
| `riskplatform.app-conventions` | aplicaciones ejecutables (`poc/no-vertx-clean-engine`) |
| `riskplatform.fatjar-conventions` | apps Vert.x que requieren fat-jars Shadow |
| `riskplatform.testing-conventions` | módulos de test standalone |

## Comandos comunes

```bash
# Compilar todo — todos los modulos, todos los tests (solo unit)
./gradlew build

# Compilar y testear un solo modulo
./gradlew :pkg:resilience:build
./gradlew :pkg:resilience:test

# Correr la CLI risk-engine
./gradlew :poc:no-vertx-clean-engine:run

# Correr el risk-engine HTTP en un puerto custom
./gradlew :poc:no-vertx-clean-engine:run --args="--port 8082"

# Construir un fat-jar Vert.x
./gradlew :poc:vertx-layer-as-pod-eventbus:controller-app:shadowJar

# Correr los tests estructurales de boundaries de ArchUnit (15 reglas, 0 fallos esperados)
./gradlew :tests:architecture:test

# Correr tests ATDD (requiere server corriendo; flag -Patdd habilita la task)
./gradlew :tests:risk-engine-atdd:test -Patdd
./gradlew :poc:vertx-layer-as-pod-eventbus:atdd-tests:test -Patdd

# Correr tests de integracion (requiere Docker; flag -Pintegration)
./gradlew :tests:integration:test -Pintegration

# Correr el benchmark JMH in-process
./gradlew :bench:inprocess-bench:shadowJar
java -jar bench/inprocess-bench/build/libs/inprocess-bench-*-all.jar

# Correr tests con output detallado
./gradlew :pkg:events:test --info

# Ver el arbol de dependencias runtime de un modulo
./gradlew :pkg:resilience:dependencies --configuration runtimeClasspath

# Chequear actualizaciones de versiones de dependencias
./gradlew dependencyUpdates

# Listar todas las tasks disponibles
./gradlew tasks --all | head -60
```

## Configuration cache

El primer build es más lento porque Gradle serializa el modelo de configuración.
Los builds siguientes saltean la configuración por completo y son casi instantáneos.
Para forzar una configuración limpia:

```bash
./gradlew build --rerun-tasks
```

## Agregar un nuevo módulo pkg (4 pasos)

1. Crear el directorio y el build file:
   ```
   mkdir -p pkg/<name>/src/main/java/io/riskplatform/poc/pkg/<name>
   echo 'plugins { id("riskplatform.library-conventions") }' > pkg/<name>/build.gradle.kts
   ```
2. Agregar `"pkg:<name>"` al bloque `include(...)` en `settings.gradle.kts`.
3. Escribir las fuentes en `pkg/<name>/src/main/java/...`.
4. Declarar la dependencia en los módulos consumidores:
   ```kotlin
   dependencies {
       implementation(project(":pkg:<name>"))
   }
   ```

## Fase 2 — completa (módulos de aplicación migrados)

La fase 2 agregó todos los módulos de aplicación y tests al reactor Gradle:

| Módulo | Convention | Descripción |
|---|---|---|
| `poc:no-vertx-clean-engine` | `riskplatform.app-conventions` | Risk engine bare-javac, Clean/Hexagonal Architecture |
| `poc:vertx-layer-as-pod-eventbus:shared` | `riskplatform.library-conventions` | DTOs / puertos compartidos |
| `poc:vertx-layer-as-pod-eventbus:controller-app` | `riskplatform.fatjar-conventions` | Vert.x HTTP + WS + SSE |
| `poc:vertx-layer-as-pod-eventbus:usecase-app` | `riskplatform.fatjar-conventions` | Verticle de evaluación de riesgo |
| `poc:vertx-layer-as-pod-eventbus:repository-app` | `riskplatform.fatjar-conventions` | Persistencia / Hazelcast |
| `poc:vertx-layer-as-pod-eventbus:consumer-app` | `riskplatform.fatjar-conventions` | Consumer Kafka |
| `poc:vertx-layer-as-pod-eventbus:atdd-tests` | `riskplatform.testing-conventions` | Suite Karate ATDD (requiere `-Patdd`) |
| `tests:risk-engine-atdd` | `riskplatform.testing-conventions` | Suite Cucumber ATDD (requiere `-Patdd`) |
| `tests:architecture` | `riskplatform.testing-conventions` | Tests ArchUnit de 15 reglas de boundaries |
| `tests:integration` | `riskplatform.testing-conventions` | Tests de integración Testcontainers (requiere `-Pintegration`) |
| `bench:inprocess-bench` | `riskplatform.fatjar-conventions` | Benchmarks JMH in-process |
| `bench:distributed-bench` | `riskplatform.fatjar-conventions` | Generador de carga HTTP |
| `bench:runner` | `riskplatform.fatjar-conventions` | Runner de reportes comparativos |

Todos los `build.gradle.kts` legacy fueron renombrados a `pom.gradle-legacy.xml` como referencia histórica.

### Violaciones de ArchUnit corregidas en la Fase 2

Se resolvieron tres violaciones estructurales que rompían los boundaries de Clean Architecture:

1. `ExecutionContext` movido de `application.common` a `domain.context` — domain ya no importa de la capa application.
2. Interfaz `CircuitBreakerPort` agregada en `application.port.out` — el use-case depende del puerto, no de la implementación concreta `infrastructure.resilience.CircuitBreaker`.
3. `CliRunner`, `HttpRunner`, `BenchmarkRunner` movidos de `infrastructure.controller` a `cmd` — rompe el ciclo `config → infrastructure → config`.

Resultado: `./gradlew :tests:architecture:test` — **15/15 PASS, 0 skipped, 0 failures**.
