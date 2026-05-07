---
title: Testing Strategy MOC
tags: [moc, testing]
created: 2026-05-07
---

# Testing Strategy MOC

## Jerarquía de filosofía

- [[TDD]] — test-first a nivel unitario, herramienta de diseño
- [[BDD]] — specs de comportamiento, lenguaje del stakeholder
- [[ATDD]] — los tests de aceptación conducen la implementación, loop más externo

## Implementaciones

- [[atdd-karate]] — Karate DSL, 10 features Gherkin, plataforma Vert.x
- [[atdd-cucumber]] — Cucumber-JVM, 7 features, motor bare-javac

## Estrategia de cobertura

- Unit: entidades de dominio, use cases (Java puro, sin contenedores)
- Integration: ports + adapters (testcontainers)
- Acceptance: E2E completo vía [[atdd-karate]] y [[atdd-cucumber]]
- Smoke: [[risk-smoke-tui]] — 9 checks en TUI runner

## Decisiones

- [[0006-atdd-karate-cucumber]] — por qué Karate y Cucumber

## Backlinks

[[Risk-Platform-Overview]] linkea acá como entry point de testing.
