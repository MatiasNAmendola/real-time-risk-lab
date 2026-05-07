---
title: TDD — Test-Driven Development
tags: [concept, testing, tdd]
created: 2026-05-07
---

# TDD

Ciclo Red-Green-Refactor a nivel unitario. Escribir un test que falla, hacerlo pasar con código mínimo, refactorizar. TDD es tanto una herramienta de diseño como de testing — fuerza unidades chicas y testeables.

## Cuándo usar

Lógica de dominio: reglas de riesgo, calculadoras, máquinas de estado. Todo código cuya correctitud es verificable de forma aislada.

## Cuándo NO usar

Código de glue, wiring de infraestructura, configuración. El costo del feedback loop supera al beneficio.

## En este proyecto

Los tests de entidades de dominio y de use cases en [[java-risk-engine]] siguen TDD. No se necesita contexto Spring — los unit tests Java puros corren en <10ms cada uno.

## Principio de diseño

"TDD en la capa de dominio es barato — sin contenedores, sin red. Los tests corren en milisegundos y te dicen exactamente qué se rompió."

## Backlinks

[[ATDD]] · [[BDD]] · [[Testing-Strategy]] · [[java-risk-engine]]
