---
title: Saga Pattern
tags: [concept, distributed-transactions, saga, compensation]
created: 2026-06-01
updated: 2026-06-01
---

# Saga Pattern

El patrón Saga modela un proceso distribuido como una secuencia de transacciones locales. No hay rollback ACID global: cada paso confirma localmente y, si algo falla, se ejecutan compensating transactions para deshacer o neutralizar los pasos previos.

## En este repo

Las PoCs TypeScript muestran una Saga orquestada:

```text
reservar inventario
→ postear ledger
→ notificar downstream
→ si falla: compensar pasos previos
```

El orchestrator vive en application/usecase y depende de puertos. Los adapters concretos quedan en infrastructure.

## Requisitos operativos

- Idempotencia.
- Trazabilidad por correlationId.
- Estados explícitos por paso.
- Compensaciones idempotentes.
- Timeouts/retries definidos por caso de negocio.

## Related

- [[Idempotency]]
- [[Outbox-Pattern]]
- [[nestjs-distributed-transactions]]
- [[hono-distributed-transactions]]
