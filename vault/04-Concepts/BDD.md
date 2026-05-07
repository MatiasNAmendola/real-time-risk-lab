---
title: BDD — Behaviour-Driven Development
tags: [concept, testing, bdd]
created: 2026-05-07
---

# BDD

Behaviour-Driven Development. Una extensión de TDD que usa notación Given/When/Then (Gherkin) para escribir escenarios en lenguaje cuasi-natural. BDD tiende un puente entre ingenieros y stakeholders de negocio.

## Cuándo usar

Features donde los criterios de aceptación provienen de stakeholders no técnicos. Las reglas de evaluación de riesgo ("given a high-risk merchant, when a large transaction occurs, then it should be flagged") son escenarios BDD naturales.

## Cuándo NO usar

Tests puramente técnicos (unit, benchmark). Gherkin agrega ceremonia sin beneficio para tests internos de componentes.

## En este proyecto

Tanto [[atdd-karate]] como [[atdd-cucumber]] usan features Gherkin. Los escenarios en `atdd-tests/` pueden ser leídos y validados por un product owner.

## Principio de diseño

"BDD es la capa de traducción entre los requerimientos de negocio y los tests ejecutables. El archivo Gherkin es a la vez documentación y especificación."

## Backlinks

[[ATDD]] · [[TDD]] · [[atdd-karate]] · [[atdd-cucumber]]
