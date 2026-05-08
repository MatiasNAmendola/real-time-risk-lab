---
title: java-risk-engine PoC
tags: [poc, java, clean-architecture]
created: 2026-05-07
source: poc/java-risk-engine/
---

# java-risk-engine

Implementación bare-javac de [[Clean-Architecture]] para un motor de riesgo transaccional. Sin framework — compila con `javac` y corre con `java`. Refactorizado al layout enterprise Go (ver [[0002-enterprise-go-layout-in-java]]).

## Qué demuestra

- Regla de dependencias de [[Clean-Architecture]] enforced por estructura de packages
- Guard de [[Idempotency]] vía store de keys de deduplicación in-memory
- Implementación manual de [[Circuit-Breaker]] sin librería
- [[ML-Online-Fallback]] — fallback determinístico cuando el modelo ML no está disponible
- Stub de [[Outbox-Pattern]] — agregado explícito (ver [[0008-outbox-pattern-explicit]])
- [[Virtual-Threads-Loom]] — servidor HTTP sobre virtual threads

## Stack

| Componente | Versión |
|------------|---------|
| Java | 25 LTS |
| Build | bare `javac` / `java` |
| HTTP | `com.sun.net.httpserver` |
| Test | JUnit 5 |
| Benchmark | JMH |

## Cómo correrlo

```bash
cd poc/java-risk-engine
poc/java-risk-engine/scripts/build.sh
poc/java-risk-engine/scripts/run.sh
# o con Gradle si está presente:
./gradlew clean verify
```

## Estructura de directorios

```
poc/java-risk-engine/
  domain/
    entity/       RiskScore, Transaction, RiskDecision
    repository/   TransactionRepository (port)
    usecase/      EvaluateRiskUseCase (port)
    service/      RiskScoringService
    rule/         VelocityRule, AmountRule
  application/
    usecase/risk/ EvaluateRiskUseCaseImpl
    mapper/       TransactionMapper
  infrastructure/
    controller/   HttpRiskController
    consumer/     SqsEventConsumer (stub)
    repository/   InMemoryTransactionRepository
    resilience/   CircuitBreaker, RetryPolicy
    time/         Clock (port + adapter SystemClock)
  cmd/            entry point Main
  config/         AppConfig
```

## Resultados de benchmark

- p50: 50µs
- p95: 127ms
- p99: 153ms
- Throughput: 1528 req/s

## Conceptos aplicados

[[Clean-Architecture]] · [[Hexagonal-Architecture]] · [[Circuit-Breaker]] · [[Idempotency]] · [[Outbox-Pattern]] · [[Virtual-Threads-Loom]] · [[ML-Online-Fallback]]

## Decisiones

[[0001-java-25-lts]] · [[0002-enterprise-go-layout-in-java]] · [[0008-outbox-pattern-explicit]]

## Talking points de diseño

- "El dominio tiene cero imports de framework. Podés mover el package `domain/` entero a otro stack y la lógica de negocio queda intacta."
- El benchmark muestra p99=153ms bien dentro del SLA de 300ms incluso en una laptop.
- El circuit breaker son 40 líneas de Java — no hace falta librería para el patrón core.
