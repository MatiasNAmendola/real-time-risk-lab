---
title: atdd-cucumber PoC
tags: [poc, testing, atdd, cucumber]
created: 2026-05-07
source: tests/risk-engine-atdd/
---

# atdd-cucumber

Suite de tests [[ATDD]] para el motor de riesgo bare-javac usando Cucumber-JVM. 7 features Gherkin con step definitions en Java. Módulo Gradle independiente.

## Qué demuestra

- Patrones de step definitions de Cucumber-JVM (Java 21 baseline, sin glue de Spring)
- Loop externo de [[ATDD]] para el motor [[Clean-Architecture]]
- Estructura de escenarios [[BDD]]: Given/When/Then
- Independencia: el módulo de tests no tiene dependencias de código productivo más allá del JAR del motor

## Stack

| Componente | Versión |
|------------|---------|
| Cucumber-JVM | 7.x |
| JUnit 5 | runner |
| Java | 25 LTS |
| Gradle | módulo único |

## Cómo correrlo

```bash
cd tests/risk-engine-atdd
./gradlew test jacocoTestReport
# reporte HTML: build/cucumber-reports/
```

## Cobertura de features (7 features)

1. Evaluar transacción de bajo riesgo — aprobada
2. Evaluar transacción de alto riesgo — rechazada
3. Transacción duplicada — respuesta idempotente
4. Modelo ML no disponible — fallback al rule engine
5. Circuit breaker se abre — respuesta degradada
6. Rate limit excedido — respuesta 429
7. Payload inválido — 400 con error estructurado

## Conceptos aplicados

[[ATDD]] · [[BDD]] · [[Idempotency]] · [[ML-Online-Fallback]] · [[Circuit-Breaker]]

## Decisiones

[[0006-atdd-karate-cucumber]]

## Talking points de diseño

- "Cada escenario Cucumber mapea a un requerimiento de negocio del JD. Si un escenario falla, sabemos exactamente qué requerimiento se rompió."
- Los step definitions usan las interfaces de los ports del motor directamente — no hace falta HTTP para tests de aceptación a nivel unitario.
