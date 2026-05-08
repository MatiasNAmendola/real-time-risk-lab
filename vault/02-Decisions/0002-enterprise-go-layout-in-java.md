---
adr: "0002"
title: Adoptar el layout enterprise Go en el PoC Java
status: accepted
date: 2026-05-07
deciders: [Mati]
tags: [decision/accepted, area/architecture, area/layout]
---

# ADR-0002: Adoptar el layout enterprise Go en el PoC Java

## Estado

Aceptado el 2026-05-07.

## Contexto

Tras estudiar el layout canónico enterprise de Go (ver [[Enterprise-Go-Layout-Reference]]), la estructura `internal/{domain,application,infrastructure}` mapea limpiamente a los anillos de Clean Architecture. El PoC inicial bare-javac usaba un layout plano — todas las clases en `io.riskplatform.engine` — que no demostraba la intención arquitectural con claridad. Un layout plano hace invisible la regla de dependencias (domain no puede depender de infrastructure): no hay estructura de packages que la codifique.

La pregunta es qué convención de layout de packages adoptar para el PoC Java. Las opciones van desde el layout convencional layer-based de Spring Boot hasta patterns tácticos DDD estrictos. El layout debe ser: (1) legible para ingenieros Go familiarizados con el layout enterprise Go, (2) consistente con los principios de Clean Architecture, y (3) usable como target de verificación con ArchUnit (los packages deben codificar los límites de capas).

## Decisión

Refactorizar `poc/no-vertx-clean-engine/` para espejar el layout canónico enterprise de Go: `domain/{entity,repository,usecase,service,rule}`, `application/usecase/risk/`, `application/mapper/`, `cmd/`, `config/`, `infrastructure/{controller,consumer,repository,resilience,time}`. El package `domain/repository` contiene interfaces (ports out); `infrastructure/repository` contiene implementaciones (adapters).

## Alternativas consideradas

### Opción A: Layout inspirado en enterprise Go (domain/application/infrastructure) (elegida)
- **Ventajas**: Inmediatamente legible para ingenieros Go; la regla de dependencias está enforced visualmente por package naming — `infrastructure` en el anillo externo, `domain` en el anillo interno; mapea a los anillos de Clean Architecture sin renombrar; las reglas ArchUnit pueden enforce `domain..*` no depende de `infrastructure..*`; resultados de benchmark fuertes (p99=153ms, 1528 req/s) — el layout no impide la performance.
- **Desventajas**: No convencional para equipos Java esperando layout Spring Boot (`com.company.service`, `com.company.repository`); el package `cmd/` es inusual en Java (idiom de Go); ingenieros con background Java pueden preguntar "¿por qué no un layout Java estándar?".
- **Por qué se eligió**: El stack target mezcla servicios Java y Go. Usar un layout familiar para ingenieros Go demostrando que los principios de Clean Architecture son universales (no específicos de Go) es la señal de diseño más fuerte.

### Opción B: Layered packages estándar de Spring Boot (package-by-layer)
- **Ventajas**: Familiar para cualquier Java developer; output estándar de archetype IntelliJ/Gradle; esperado por convención Spring Boot; no requiere explicación.
- **Desventajas**: Package-by-layer mezcla niveles de abstracción: `io.riskplatform.service` contiene tanto domain services como application services; `io.riskplatform.repository` contiene tanto interfaces de ports como implementaciones de adapters; la regla de dependencias no es visible — una clase de `service` puede importar desde `controller` sin ningún indicador estructural de violación.
- **Por qué no**: Package-by-layer oculta la intención arquitectural. Un reviewer no puede decir si la arquitectura es Clean Architecture, Hexagonal, o spaghetti plano solo a partir de los package names.

### Opción C: Patrones tácticos DDD (Aggregate, ValueObject, interfaces Repository explícitas en el domain package)
- **Ventajas**: Más completo arquitecturalmente; los límites de Aggregate explícitos imponen los límites de transacción; los Value Objects previenen la obsesión por primitivos; la implementación DDD más correcta semánticamente.
- **Desventajas**: Sobre-ingenierizado para un PoC con un solo aggregate; `TransactionId`, `CustomerId`, `Money` como Value Objects explícitos suma 10+ clases antes de cualquier comportamiento; un Aggregate root con domain events requiere un mecanismo de publicación de eventos separado del outbox — dos paths de eventos para explicar.
- **Por qué no**: El scope del PoC es un motor de evaluación de riesgo, no una implementación DDD completa. Los patterns tácticos DDD son la elección correcta para el sistema productivo; para el PoC, el layout estructural (que impone la regla de dependencias) es más valioso que la completitud de patterns tácticos.

### Opción D: Hexagonal Architecture (ports y adapters, naming explícito)
- **Ventajas**: Naming explícito de ports (`RiskEvaluationPort`, `FeatureRepositoryPort`); adapters en `adapters/inbound/` y `adapters/outbound/`; naming hexagonal estándar que muchos arquitectos reconocen.
- **Desventajas**: Los package names `ports/` y `adapters/` son menos familiares en Java que en otros ecosistemas; más verboso que el layout enterprise Go; se pierde la analogía con el layout Go.
- **Por qué no**: El naming hexagonal es válido pero agrega vocabulario sin agregar claridad arquitectural por sobre el layout enterprise Go en este codebase. La regla de dependencias es equivalente; el naming es distinto.

## Consecuencias

### Positivo
- La regla ArchUnit `noClasses().that().resideInAPackage("..domain..")` enforce-a la regla de dependencias mecánicamente.
- Los ingenieros Go leyendo `poc/no-vertx-clean-engine/` reconocen el layout inmediatamente.
- Las interfaces de `domain/repository/` son los puntos de inversión de dependencias — claramente visibles, no enterradas en `service/`.
- El package `cmd/` establece la convención del entry point (análogo a `cmd/main.go`).

### Negativo
- Java developers esperando `com.company.service` ven `application/usecase/risk/` — requiere explicación.
- `cmd/` es inusual en Java; el IDE puede no autogenerar sugerencias de clase main en este package.
- El layout requiere disciplina: el primer reflejo Spring Boot es poner cosas en `service/`.

### Mitigaciones
- El `poc/no-vertx-clean-engine/README.md` explica el layout y la analogía con Go.
- Doc 04 documenta el mapping del layout Go al layout Java en detalle.
- Tests ArchUnit imponen el layout mecánicamente.

## Validación

- El test ArchUnit `BareJavacArchitectureTest` pasa con la regla de domain-no-importa-infrastructure.
- La estructura de packages es visible en el output de `find poc/no-vertx-clean-engine/src -type d`.
- Doc 04 (`docs/04-clean-architecture-java.md`) documenta el mapping del layout Go-a-Java.

## Relacionado

- [[0036-archunit-structural-verification]]
- [[0031-no-di-framework]]
- [[Clean-Architecture]]
- [[Hexagonal-Architecture]]
- Docs: doc 04 (`docs/04-clean-architecture-java.md`)

## Referencias

- Clean Architecture (Robert C. Martin): https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html
- doc 04: `docs/04-clean-architecture-java.md`
