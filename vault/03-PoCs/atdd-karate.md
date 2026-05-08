---
title: atdd-karate PoC
tags: [poc, testing, atdd, karate]
created: 2026-05-07
source: poc/vertx-layer-as-pod-eventbus/atdd-tests/
---

# atdd-karate

Suite de tests [[ATDD]] para la plataforma Vert.x distribuida usando Karate DSL. 10 features Gherkin que cubren todos los canales de comunicación + cobertura JaCoCo cross-module.

## Qué demuestra

- Filosofía [[ATDD]]: tests de aceptación escritos antes o junto a la implementación
- Soporte built-in de Karate para HTTP, WebSocket y aserciones async
- Agregación de cobertura JaCoCo cross-module
- Gherkin [[BDD]] legible por no-ingenieros

## Stack

| Componente | Versión |
|------------|---------|
| Karate DSL | 1.4+ |
| JUnit 5 | runner |
| JaCoCo | cross-module |
| Java | 25 LTS |

## Cómo correrlo

```bash
cd poc/vertx-layer-as-pod-eventbus/atdd-tests
./gradlew test jacocoTestReport
# reporte de cobertura: build/reports/jacoco/test-aggregate/index.html
```

## Cobertura de features (10 features)

1. Evaluación de riesgo — happy path
2. Evaluación de riesgo — transacción de alto riesgo bloqueada
3. Evaluación de riesgo — idempotencia (request duplicado)
4. Circuit breaker — fallback en estado open
5. Stream SSE — primer evento recibido
6. WebSocket — round-trip bidireccional
7. Webhook — entrega del callback
8. Round-trip Kafka — produce + consume
9. Propagación de trace OTEL
10. Validación de contrato OpenAPI

## Conceptos aplicados

[[ATDD]] · [[BDD]] · [[Idempotency]] · [[Circuit-Breaker]] · [[Communication-Patterns]] · [[Correlation-ID-Propagation]]

## Decisiones

[[0006-atdd-karate-cucumber]]

## Talking points de diseño

- "Las features de Karate son la especificación ejecutable. Corren en cada PR y atrapan regresiones antes del merge."
- JaCoCo cross-module muestra cobertura cruzando controller → usecase → repository, no solo por módulo.
