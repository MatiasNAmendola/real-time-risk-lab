#!/usr/bin/env bash
set -euo pipefail
cat <<'MSG'
Comparación conceptual:

NestJS:
- Controllers/decorators.
- DI container del framework.
- @nestjs/cqrs para command/event handlers.
- class-validator en DTOs de infraestructura.

Hono:
- Rutas explícitas y minimalistas.
- Wiring manual en src/cmd/container.ts.
- Command bus simple propio.
- Zod en rutas de infraestructura.

Ambas PoCs comparten la intención Clean Architecture:
- domain/application sin framework.
- infrastructure concentra HTTP, validation, queues, repositories y adapters.
MSG
