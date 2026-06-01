---
title: Screaming Architecture
tags: [concept/architecture, clean-architecture, screaming-architecture]
created: 2026-06-01
---

# Screaming Architecture

Screaming Architecture significa que la estructura del sistema comunica primero el negocio y recién después el framework, la capa o la tecnología.

En este repo no reemplaza a Clean Architecture: la complementa. La regla práctica es:

> Primero la capacidad de negocio; después domain/application/infrastructure o el adapter técnico.

## Aplicación en el repo

### Java / Vert.x

Los paquetes Java ahora exponen la capacidad `riskdecision` antes de la topología:

```text
io.riskplatform.riskdecision.cleanengine
io.riskplatform.riskdecision.monolith
io.riskplatform.riskdecision.layerpodhttp
io.riskplatform.riskdecision.layerpodeventbus
```

Los bounded contexts de service mesh ya seguían esta idea:

```text
risk-decision-service
fraud-rules-service
ml-scorer-service
audit-service
```

### NestJS / Hono

Las PoCs TypeScript mantienen el contraste de framework en el nombre de la PoC, pero el código fuente grita la capacidad primero:

```text
src/internal/transactional-risk/domain
src/internal/transactional-risk/application
src/internal/transactional-risk/infrastructure
```

Así se preserva Clean Architecture sin hacer que `domain`, `application` o `infrastructure` sean lo primero que se ve.

## Regla de diseño

- El nombre de la PoC puede describir el experimento: stack, runtime o topología.
- El source tree del servicio debe describir primero la capacidad de negocio.
- Las capas técnicas siguen existiendo, pero debajo de esa capacidad.
- Los adapters/frameworks no deben aparecer en domain/application.

## Trade-off

En un repo comparativo, algunos nombres técnicos son deliberados porque ayudan a discutir la diferencia entre Vert.x, Hono, NestJS, HTTP, EventBus o layer-as-pod. La decisión fue no ocultar esos nombres en el inventario de PoCs, pero sí hacer que el código de cada servicio apunte primero al dominio.

## Relacionado

- [[Clean-Architecture]]
- [[Hexagonal-Architecture]]
- [[Enterprise-Go-Layout-Reference]]
- [[CQRS-Event-Sourcing]]
