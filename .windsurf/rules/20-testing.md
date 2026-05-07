---
trigger: glob
glob: "**/*.feature"
description: ATDD-first testing strategy
---

# Testing rules

Full rule: .ai/primitives/rules/testing-atdd.md

## ATDD first

1. Escribir .feature ANTES del codigo de produccion.
2. Run -> FAIL (RED confirmed).
3. Implementar minimo para pasar.
4. Run -> PASS (GREEN).

## Frameworks

Karate 1.5+ (PoCs), Cucumber-JVM 7+ (tests/), JUnit 5 (unit).
Coverage: >= 80% line en domain/ y application/.
