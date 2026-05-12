---
title: In-Process vs Distributed — trade-offs arquitectónicos
tags: [concept, pattern/architecture, distributed-systems, performance, isolation, vertx]
created: 2026-05-12
source_archive: docs/12-rendimiento-y-separacion.md (migrado 2026-05-12, parte conceptual)
---

# In-Process vs Distributed — trade-offs arquitectónicos

## Resumen ejecutivo

Dos PoCs implementan la misma lógica de riesgo (APPROVE / REVIEW / DECLINE) con layouts radicalmente distintos. El overhead real de la separación física es ~19 ms por request (sin el scorer ML). El scorer ML ficticio (~150 ms) domina ambas arquitecturas: optimizar el layout antes de sacar el cuello de botella ML es medir sombras.

---

## Análisis: donde se gasta cada milisegundo

### In-process

Un solo JVM, un stack frame por capa, cero serialization. La ruta crítica es:

```
HTTP handler → controller (in-mem dispatch) → EvaluateTransactionRiskService
            → FeatureProvider (in-mem map) → FakeRiskModelScorer (sleep ~0-150 ms)
            → InMemoryRiskDecisionRepository → OutboxDecisionEventPublisher
```

El cuello es exclusivamente el scorer ML. Todo lo demás suma menos de 1 ms (medido: p99 sin scorer = ~459 ns).

### Distributed (Vert.x)

Cinco JVMs conectados por Hazelcast TCP event bus:

```
HTTP client → controller-app (HttpVerticle)
           --[event bus serialize]--> usecase-app (EvaluateRiskVerticle)
           --[event bus serialize]--> repository-app (FeatureRepositoryVerticle)
           <--[event bus reply]------ repository-app
           → FakeRiskModelScorer (mismo sleep)
           <--[event bus reply]------ usecase-app
           → controller-app (HTTP response)
```

### Presupuesto de latencia por hop

| Hop | In-process | Distributed | Delta |
|-----|-----------|-------------|-------|
| HTTP parse + validate | n/a | ~3 ms | +3 ms |
| Event bus serialize controller -> usecase | 0 | ~2 ms | +2 ms |
| Rules eval (HighAmount, NewDeviceYoungCustomer) | < 1 ms | < 1 ms | 0 |
| Event bus serialize usecase -> repository | 0 | ~2 ms | +2 ms |
| DB / feature lookup (in-mem vs. Postgres) | < 1 ms | ~5 ms | +4 ms |
| Return path (2 reply hops) | 0 | ~6 ms | +6 ms |
| ML scorer (FakeRiskModelScorer) | 0-150 ms | 0-150 ms | 0 |
| Total overhead (sin ML) | < 1 ms | ~20 ms | +19 ms |

Con el scorer ML activo el overhead relativo baja de >1000x a ~1.1x en p99 porque el scorer domina ambos.

---

## Cuándo gana cada arquitectura

| Caso de uso | Ganador | Razón |
|-------------|---------|-------|
| Latencia ultra-baja, lógica acotada | In-process | sin overhead de hops |
| Equipo único, deploy simple | In-process | 1 binario, menos operaciones |
| Permisos diferenciados por capa | Distributed | repository es el único con credenciales de DB |
| Scaling independiente (controller HTTP-bound, usecase CPU-bound) | Distributed | cada layer escala distinto |
| Failure isolation (crash no derriba todo) | Distributed | bulkhead físico |
| Equipos que ownean capas distintas | Distributed | Conway's law |
| Reducción de blast radius en seguridad | Distributed | compromiso del controller no alcanza la DB |
| Cold start < 200 ms | In-process (o GraalVM native) | 5 JVMs arrancan en serie |

---

## Por qué HTTP no es la mejor forma de comunicar pods internos

En este PoC el inter-pod va por Vert.x event bus sobre Hazelcast TCP, no HTTP. Ventajas:

- Sin headers HTTP por hop (~150-300 bytes ahorrados por mensaje).
- Sin parsing repetido: se parsea JSON una vez en el ingress; después viaja como buffer en el bus.
- Multiplexado nativo sobre conexión TCP persistente sin handshake por request.

### Alternativas mejores que HTTP para inter-pod

| Opcion | Cuando usarla |
|--------|--------------|
| Vert.x event bus (lo que usamos) | Best fit con Vert.x; misma JVM o TCP cluster |
| gRPC | Protobuf binario, HTTP/2 multiplex, contratos tipados con codegen; ideal para polyglot |
| Unix domain sockets | Same node only; latencia más baja que TCP; no aplica en K8s multi-nodo |
| Kafka | Asíncrono desacoplado; no para sync request-response |

> "HTTP es para cliente-servidor cruzando organizaciones. Entre pods de un mismo servicio no hay razón para pagar el costo de HTTP cada hop."

---

## Cómo verificamos que la separación es REAL (no decorativa)

`tests/architecture/` contiene 15 reglas ArchUnit que se ejecutan con `./gradlew test`.

Las tres violaciones detectadas por ArchUnit en el bare-javac (deuda técnica activa):

1. `domain.usecase.EvaluateRiskUseCase` importa `application.common.ExecutionContext` — domain depende de application.
2. `application.usecase.risk.EvaluateTransactionRiskService` inyecta `infrastructure.resilience.CircuitBreaker` directo (debería ir vía port).
3. Ciclo `application -> domain -> application` (cascada de la violación #1).

> "Antes de correr ArchUnit creía que la separación estaba bien. Tres violaciones más tarde, sé que no. Esa es la diferencia entre arquitectura documentada y arquitectura enforced."

---

## Key Design Principles

- "Separar capas en pods diferentes es pagar latencia para comprar isolation."
- "El blast radius de un crash es proporcional al tamaño del proceso."
- "El permiso de DB nunca debería estar en el mismo proceso que el handler HTTP."
- "Cada capa escala distinto: el controller es HTTP-bound, el usecase es CPU-bound, el repository es DB-bound. Mezclarlas en un solo binario es escalar la peor de las tres."
- "ArchUnit convierte la arquitectura en un test. Si pasa, la separación es real. Si falla, el código tiene atajos."

## Related

- [[Poc-Parity-Matrix]] — tabla completa de paridad funcional y performance entre PoCs.
- [[Layer-as-Pod]] — concepto y arquitectura de separación por capas.
- [[Clean-Architecture]] — boundaries y capas de la arquitectura limpia.
- [[Virtual-Threads-Loom]] — alternativa para concurrencia en Java.
- [[Risk-Platform-Overview]]
