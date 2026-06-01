# 43 — Auditoría transversal de apps y PoCs

## Criterio de lectura

- **OK**: cumple lo esperable para el tipo de PoC auditada.
- **WARN**: no hay violación bloqueante, pero existe deuda aceptada o una decisión intencional que hay que poder explicar.
- **FAIL**: violación bloqueante; no debería entrar a una review exigente sin fix.
- **N/A**: no aplica porque la PoC es infraestructura, broker o tooling, no una app de dominio.

La auditoría distingue entre **Clean Architecture estricta** y **arquitecturas comparativas**. No todas las PoCs tienen que verse iguales: algunas existen para contrastar monolito, layer-as-pod, HTTP entre pods, EventBus clustered, service-to-service, infraestructura k8s o broker Kafka/S3.

> Snapshot generado por `.ai/scripts/app-compliance-audit.py`. Es un guardrail offline: valida estructura, documentación mínima, superficie de tests, boundaries de código fuente y pinning básico de imágenes Compose sin ejecutar builds ni red.

## Matriz de cumplimiento por PoC

| App/PoC | Tipo | Regla auditada | Docs | Build | Tests | Boundaries | Compose | Estado |
|---|---|---|---:|---:|---:|---:|---:|---:|
| `poc/no-vertx-clean-engine` | Java app | Clean Architecture estricta | OK | OK | OK | OK | N/A | **OK** |
| `poc/vertx-monolith-inprocess` | Java Vert.x app | Monolito modular; adapters en repository/ y unit/integration tests | OK | OK | OK | WARN | OK | **WARN** |
| `poc/vertx-layer-as-pod-eventbus` | Java distributed app | Separación física por capas + shared module | OK | OK | OK | OK | OK | **WARN** |
| `poc/vertx-layer-as-pod-http` | Java distributed app | HTTP layer-as-pod + tokens | OK | OK | OK | WARN | OK | **WARN** |
| `poc/vertx-service-mesh-bounded-contexts` | Java service mesh app | Bounded contexts separados por servicio | OK | OK | OK | OK | OK | **WARN** |
| `poc/k8s-local` | Kubernetes platform PoC | Infraestructura/GitOps, no aplicación de dominio | OK | N/A | OK | N/A | N/A | **WARN** |
| `poc/kafka-s3-tansu` | Kafka/S3 broker PoC | Infraestructura broker, no aplicación de dominio | OK | OK | OK | N/A | OK | **WARN** |
| `poc/nestjs-distributed-transactions` | TypeScript NestJS app | Clean Architecture con internal/domain|application|infrastructure | OK | OK | OK | OK | OK | **OK** |
| `poc/hono-distributed-transactions` | TypeScript Hono app | Clean Architecture con wiring manual | OK | OK | OK | OK | OK | **OK** |
| `cli/risk-smoke` | Go CLI | CLI internal packages con dependencias dirigidas | OK | OK | OK | N/A | N/A | **WARN** |

## Violaciones encontradas

- No hay violaciones bloqueantes detectadas por el guardrail offline.

## Fixes rápidos recomendados

- Correr `python3 .ai/scripts/app-compliance-audit.py` antes de una review exigente o de tocar una PoC.
- Correr `python3 .ai/scripts/quick-check.py` para boundaries fuente rápidos y freshness de artefactos.
- Correr `./nx test architecture` para ArchUnit bytecode/source-level en Java.
- Correr `./nx test --composite typescript-transactional-pocs --parallel 1 --max-cpu 50 --max-ram 4000` si se toca NestJS/Hono.
- Para PoCs con deuda aceptada, promover primero las warnings a tests ejecutables antes de agregar funcionalidad nueva.

## Warnings y deuda aceptada explícitamente

- `poc/vertx-monolith-inprocess`: No usa layout domain/application/infrastructure completo porque contrasta un monolito Vert.x in-process.
- `poc/vertx-layer-as-pod-eventbus`: La regla principal es aislamiento entre JVMs/módulos, no layout Clean Architecture dentro de cada módulo.
- `poc/vertx-layer-as-pod-http`: Persistencia in-memory por diseño para aislar la discusión HTTP+tokens vs EventBus.
- `poc/vertx-service-mesh-bounded-contexts`: PoC mínima: falta suite k6/ATDD dedicada comparable a las otras PoCs.
- `poc/k8s-local`: No aplica Clean Architecture; se audita como infraestructura declarativa.
- `poc/kafka-s3-tansu`: Mantiene texto en inglés como referencia histórica upstream; no bloquea la demo principal.
- `cli/risk-smoke`: La documentación del CLI sigue en inglés; conviene traducirla si se busca consistencia total de docs.

## Guardrails automáticos disponibles

| Guardrail | Comando | Qué cubre |
|---|---|---|
| Auditoría transversal offline | `python3 .ai/scripts/app-compliance-audit.py` | Matriz PoC, docs/build/tests mínimos, boundaries Java/TS y Compose pinning. |
| Quick check de demo | `python3 .ai/scripts/quick-check.py` | Boundaries críticos Java/TS/Go + freshness de build artifacts. |
| Primitivas IA | `./.ai/scripts/verify-primitives.sh` | Frontmatter, links, estructura de reglas/skills/workflows. |
| Arquitectura Java | `./nx test architecture` | ArchUnit y reglas estructurales Java. |
| Suite TS transaccional | `./nx test --composite typescript-transactional-pocs --parallel 1 --max-cpu 50 --max-ram 4000` | Unit/integration/e2e/ATDD/smoke/k6 NestJS y Hono. |
| CI rápido compuesto | `./nx test --composite ci-fast --parallel 1 --max-cpu 50 --max-ram 4000` | Smoke/arquitectura/tests rápidos del repo. |
