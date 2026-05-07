---
title: ATDD — Acceptance Test-Driven Development
tags: [concept, testing, atdd]
created: 2026-05-07
source: docs/11-atdd.md
---

# ATDD

Acceptance Test-Driven Development. El loop TDD más externo: escribir tests de aceptación (en lenguaje de negocio) antes de escribir la implementación. Los tests de aceptación definen "done" desde la perspectiva del stakeholder.

## Cuándo usar

Features con criterios de aceptación de negocio claros. La evaluación de riesgo ("una transacción mayor a $10k de un merchant nuevo debería ser flaggeada") mapea perfectamente.

## Cuándo NO usar

Cambios puramente de infraestructura sin comportamiento de negocio observable.

## Jerarquía

ATDD (más externo) envuelve a [[TDD]] (nivel unitario). [[BDD]] es el sistema de notación usado en ambos.

## En este proyecto

[[atdd-karate]] (Karate DSL, 10 features para Vert.x) y [[atdd-cucumber]] (Cucumber-JVM, 7 features para bare-javac). Documentado en `docs/11-atdd.md`.

## Principio de diseño

"ATDD cierra el loop entre los requerimientos de negocio y la cobertura de tests. Si el test de aceptación pasa, el requerimiento está cumplido — por definición."

## Backlinks

[[TDD]] · [[BDD]] · [[Testing-Strategy]] · [[atdd-karate]] · [[atdd-cucumber]] · [[0006-atdd-karate-cucumber]]
