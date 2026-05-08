# tests/architecture — ArchUnit Boundary Tests

Structural enforcement for both Java PoCs using ArchUnit 1.3.0 + JUnit 5.

## What is tested

### BareJavacArchitectureTest (9 rules)

| # | Rule |
|---|------|
| 1 | domain.* does not depend on infrastructure.* |
| 2 | domain.* does not depend on application.* |
| 3 | application.* does not depend on infrastructure adapter implementations |
| 4 | Only cmd.* and config.* may reference both domain and infrastructure |
| 5a | *UseCase (non-interface) classes reside in application.usecase.* |
| 5b | *Repository interfaces reside in domain.repository.* |
| 5c | *Controller classes reside in infrastructure.controller.* |
| 6 | No dependency cycles between top-level slices |
| 7 | domain.* does not use java.util.logging directly |

### VertxDistributedArchitectureTest (6 rules)

| # | Rule |
|---|------|
| 1a | controller-app does not import usecase-app / repository-app / consumer-app |
| 1b | usecase-app does not import controller-app / repository-app / consumer-app |
| 1c | repository-app does not import other concrete modules |
| 1d | consumer-app does not import other concrete modules |
| 2 | shared module does not import any concrete module |
| 3 | *Main classes reside at module root package depth |

Total: 15 ArchUnit rules across 2 test classes.

## Running

```bash
# Bare-javac tests only (no Docker needed)
cd tests/architecture && ./gradlew test -Dtest=BareJavacArchitectureTest

# Vert.x tests (requires compiled Vert.x modules)
cd poc/vertx-layer-as-pod-eventbus && ./gradlew shadowJar
cd ../../tests/architecture && ./gradlew test -Dtest=VertxDistributedArchitectureTest

# All tests
cd tests/architecture && ./gradlew test
```

## Failure messages

When a rule fails, ArchUnit reports:
- The exact class that violates the rule
- The exact dependency line (class + method/field)
- The rule description and the `because(...)` explanation with fix guidance

Example failure:
```
Architecture Violation [Priority: MEDIUM] - Rule 'application.* must not import
infrastructure.repository.* concrete adapters' was violated (1 times):
  Method <io.riskplatform.engine.application.usecase.risk.SomeService.doThing()>
  calls constructor <io.riskplatform.engine.infrastructure.repository.ml.FakeRiskModelScorer.<init>()>
  in (SomeService.java:42)
```

Fix guidance is embedded in the `because(...)` clause of each rule.
