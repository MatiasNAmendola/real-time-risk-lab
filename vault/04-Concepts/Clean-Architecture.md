---
title: Clean Architecture
tags: [concept, pattern/structural, architecture]
created: 2026-05-07
source: docs/04-clean-architecture-java.md
---

# Clean Architecture

Modelo de anillos concéntricos donde los anillos internos (domain, use cases) tienen cero dependencias hacia los externos (frameworks, DB, HTTP). La Regla de Dependencias: las dependencias del código fuente apuntan únicamente hacia adentro.

## Cuándo usar

Codebases donde las reglas de negocio deben sobrevivir al churn de frameworks. Motores de riesgo, procesadores de pagos — lógica que perdura más que el framework HTTP.

## Cuándo NO usar

APIs CRUD sin complejidad de dominio. El costo de ceremonia supera al beneficio.

## En este proyecto

`poc/java-risk-engine/` lo impone vía package naming: `domain/` tiene cero `import` referenciando a `infrastructure/`. Ver [[java-risk-engine]] y [[0002-enterprise-go-layout-in-java]].

## Principio de diseño

"El package domain no tiene ningún `import` que mencione HTTP, Spring o SQL. Esa es la regla de dependencias en la práctica, no en la teoría."

## Backlinks

[[Architecture]] · [[Hexagonal-Architecture]] · [[java-risk-engine]] · [[0002-enterprise-go-layout-in-java]]
