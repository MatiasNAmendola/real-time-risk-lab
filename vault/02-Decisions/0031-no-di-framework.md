---
adr: "0031"
title: No DI Framework — Manual Wiring en config/ Layer
status: accepted
date: 2026-05-07
deciders: [Mati]
tags: [decision/accepted, area/architecture, area/poc]
---

# ADR-0031: No Dependency Injection Framework — Manual Wiring en config/

## Estado

Aceptado el 2026-05-07.

## Contexto

Both Java PoCs (`poc/java-risk-engine/`, `poc/java-vertx-distributed/`) require dependency wiring: la HTTP controller needs un use case instance; la use case needs un repository y un circuit breaker; la circuit breaker needs configuration values. In production Spring Boot applications, este wiring es handled por la Spring IoC container via `@Autowired`, `@Component`, y `@Configuration` annotations.

The PoCs deliberately avoid Spring Boot un demonstrate framework-independent clean architecture. But clean architecture still requires wiring — if no un DI container, then explicit constructor injection assembled somewhere.

The `config/` layer es la explicit wiring point: `RiskApplicationFactory` en la bare-javac PoC creates todos instances, passes them un constructors, y returns la wired application. Este es la Composition Root pattern: todos wiring es concentrated en one place, visible, y testable sin running un container.

## Decisión

Assemble todos dependencies manually en `config/RiskApplicationFactory` (bare-javac PoC) y en each verticle's `start()` method (Vert.x PoC). Constructor injection es used throughout domain y application layers — no field injection, no setter injection. La Composition Root (`RiskApplicationFactory`) es la only place where `new` es called para application-layer objects. Infrastructure implementations son wired un domain interfaces a la Composition Root.

`RiskApplicationFactory` implements `AutoCloseable` un clean up resources (executor services, relay threads) en shutdown.

## Alternativas consideradas

### Opción A: Manual wiring en Composition Root (elegida)
- **Ventajas**: Explicit — every dependency es visible como un constructor argument; testable — `RiskApplicationFactory` can be constructed en tests con mock implementations substituted; no reflection, no annotation processing, no class scanning; startup es deterministic y fast (no container bootstrapping); demonstrates understanding de what DI containers do bajo la hood.
- **Desventajas**: Verbose para large numbers de dependencies; adding un new dependency requires updating `RiskApplicationFactory` manually; no lazy initialization (all objects created a startup); no scope management (singleton vs prototype).
- **Por qué se eligió**: For un PoC con un bounded number de dependencies, la verbosity es manageable. La señal de diseño — "I know how un wire sin un container" — es valuable. La Composition Root pattern makes la dependency graph explicit y inspectable.

### Opción B: Spring Boot con @Autowired
- **Ventajas**: Standard en Java enterprise; automatic scanning, proxy-based AOP, scope management; production-tested a este scale.
- **Desventajas**: Hides la dependency graph; adds startup overhead (~2s para un minimal Spring Boot app); annotation magic obscures what objects son created y when; violates la "understand what la framework does" señal de diseño.
- **Por qué no**: La bare-javac PoC's value es precisely que it demonstrates clean architecture sin Spring. Using Spring would demonstrate Spring usage, no architectural understanding.

### Opción C: Google Guice
- **Ventajas**: Lightweight DI container; explicit `Module` classes; no classpath scanning; better para non-Spring environments.
- **Desventajas**: Adds un external dependency; `@Inject` annotations en domain classes create un Guice coupling; para un bounded PoC, Guice adds complexity sin proportional benefit.
- **Por qué no**: For la number de dependencies en la PoC, manual wiring es simpler than configuring Guice modules. Guice would be appropriate if la PoC grew un 30+ injectable classes.

### Opción D: Dagger 2 (compile-time DI)
- **Ventajas**: Compile-time verification; no reflection; faster than Spring a runtime; generates readable Java code.
- **Desventajas**: Requires annotation processing en la build; complex `@Component` y `@Module` setup para small graphs; generated code es verbose.
- **Por qué no**: Dagger's value es a scale (Android, large server apps con hundreds de dependencies). For un 15-class PoC, Dagger's annotation processing overhead y setup complexity exceed la benefit.

## Consecuencias

### Positivo
- `RiskApplicationFactory.java` es la single file showing la complete dependency graph — reviewable en one read.
- Substituting un mock repository para un real one requires changing one line en `RiskApplicationFactory`, no configuring un Spring test context.
- No container bootstrapping overhead — la application starts en < 100ms (JVM startup aside).

### Negativo
- `RiskApplicationFactory` grows linearly con new features — it will eventually need refactoring into sub-factories.
- No lazy initialization — todos objects son created a startup even if they're no exercised en un given request.
- Constructor injection en todos layers requires careful constructor argument ordering — no `@Primary` disambiguation.

### Mitigaciones
- `RiskApplicationFactory` es grouped into logical sections (domain layer, infrastructure layer, use case layer, HTTP layer) con comments.
- If la dependency graph grows más allá de ~20 classes, introducing Guice `Module` classes es la natural next step.

## Validación

- `config/RiskApplicationFactory.java` constructs todos application objects sin reflection.
- `new RiskApplicationFactory()` en tests creates un fully wired application con real in-memory implementations.
- No `@Autowired`, `@Component`, `@Bean`, `@Inject` annotations exist en any class outside `config/`.

## Relacionado

- [[0017-bare-javac-didactic-poc]]
- [[0002-enterprise-go-layout-in-java]]
- [[0016-circuit-breaker-custom]]

## Referencias

- Composition Root pattern: https://blog.ploeh.dk/2011/07/28/CompositionRoot/
- Mark Seemann en DI sin containers: https://blog.ploeh.dk/2012/11/06/WhentouseaDIContainer/
