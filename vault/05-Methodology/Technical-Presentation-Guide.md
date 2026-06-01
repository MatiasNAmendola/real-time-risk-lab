---
title: Technical Presentation Guide — guion de presentación técnica
tags: [methodology/technical-discussion, presentation, demo]
created: 2026-06-01
---

# Technical Presentation Guide — guion de presentación técnica

Este nodo resume cómo presentar el laboratorio en discusiones técnicas sin convertir la conversación en un recorrido archivo por archivo.

## Relato en tres bloques

### 1. Java / Vert.x / Clean Architecture

- Riesgo/fraude en tiempo real.
- 150 TPS sostenidos.
- p99 < 300 ms.
- Decisión sincrónica y auditoría/eventos/ML/downstream asíncronos.
- ATDD, observabilidad, resiliencia y boundaries limpios.

Mensaje central:

> El valor está en separar el camino crítico de lo asíncrono y poder comparar topologías con evidencia.

### 2. NestJS / Hono

- Contraste entre framework opinionado con DI/decorators y wiring manual.
- CQRS/Event Sourcing simple con cuenta bancaria.
- Demo avanzada separada para Saga, compensaciones, idempotencia, BullMQ/Valkey y TigerBeetle como frontera de ledger.

Mensaje central:

> El mismo dominio permite discutir ergonomía, explicitud, testabilidad y costo accidental de cada stack.

### 3. Repo como sistema de ingeniería

- Primitivas para agentes e IDEs.
- Documentación metódica.
- Guardrails automáticos.
- Tests unitarios, integración, e2e, ATDD, smoke y k6.
- Scripts seguros de lifecycle.

Mensaje central:

> No sólo hay PoCs: hay método para operar, verificar, documentar y evolucionar el sistema.

## Recorrido recomendado

- 5 minutos: posicionamiento + caso + quick check + request HTTP.
- 15 minutos: sumar benchmark, separación por capas y mapa NestJS/Hono.
- 30 minutos: sumar Saga, idempotencia, auditoría transversal y deuda aceptada.


## Pulido final

- Preparar un guion corto de demo de 10 a 15 minutos.
- Elegir 2 o 3 PoCs máximo para no dispersar.
- Separar demo principal de deep dive opcional.
- Tener listos comandos de arranque, test y apagado.
- Evitar mencionar empresas, clientes o motivaciones específicas en la conversación o en material de apoyo.

## Relacionado

- [[Technical-Positioning]]
- [[Technical-Discussion-Simulation]]
- [[Architecture-Question-Bank]]
- [[App-Compliance-Audit]]
- [[CQRS-Event-Sourcing]]
- [[Saga-Pattern]]
- [[TigerBeetle-Ledger]]

Ver también: `docs/44-guion-presentacion-tecnica.md`.
