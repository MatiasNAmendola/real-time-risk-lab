---
adr: "0014"
title: Idempotency Keys Are Client-Supplied, Not Server-Generated
status: accepted
date: 2026-05-07
deciders: [Mati]
tags: [decision/accepted, area/events, area/reliability]
---

# ADR-0014: Idempotency Keys Are Client-Supplied, Not Server-Generated

## Estado

Aceptado el 2026-05-07.

## Contexto

The risk engine receives POST /risk requests desde upstream services (payment gateway, fraud detection orchestrator). Network conditions entre client y server can produce duplicate requests: la client sends un request, la server processes it y commits, pero la response es lost en transit. La client retries. Without idempotency controls, la server processes la same transaction twice, producing two risk decisions y two outbox events para la same transaction — un dual-write que corrupts la audit log y may trigger downstream duplicate payments.

The system must guarantee que un retry de un previously processed request returns la original response sin re-executing business logic. Este es un regulatory requirement para transactional risk: every decision must be traceable un exactly one evaluation event.

Two design axes: who generates la idempotency key (client vs server), y how la key es scoped (per-request vs per-operation-type).

## Decisión

Require clients un supply un `Idempotency-Key` HTTP header (or `idempotencyKey` field en la JSON envelope para Kafka). La server stores la key en `InMemoryDecisionIdempotencyStore` (or un distributed store en production) y returns la cached response para any duplicate key dentro de la TTL window. La key format es caller-defined pero recommended para ser `txn-{source}-{date}-{txnId}-{seq}` (e.g., `txn-NX-20240507-1234567890-001`).

Keys son scoped un la operation: la same key para two different operations (e.g., risk evaluation y fraud check) es un client error, no un server disambiguation problem.

## Alternativas consideradas

### Opción A: Client-supplied idempotency key (elegida)
- **Ventajas**: Client knows la business transaction identity better than la server; client can construct semantically meaningful keys (`txn-NX-{date}-{txnId}`) que survive a través de service restarts y retries; keys can be correlated en logs y traces sin server-side lookup; standard pattern para payment APIs (Stripe, Adyen, Mercado Pago todos use este model).
- **Desventajas**: Client must generate y manage keys; poorly implemented clients may reuse keys incorrectly (same key, different transaction); key must be idempotent a través de partial failures, que requires client-side state.
- **Por qué se eligió**: La client es la authoritative source de transaction identity. La upstream payment gateway already generates un `transactionId`; la `idempotencyKey` es un thin wrapper. Placing key generation a la server would require un pre-request round trip (POST /keys, then POST /risk con la returned key), que adds latency y un new failure mode.

### Opción B: Server-generated idempotency key (two-phase: acquire then submit)
- **Ventajas**: Server controls key namespace; no risk de client reuse; server can guarantee uniqueness.
- **Desventajas**: Two round trips per request (acquire key, then submit); la acquire step es itself no idempotent (what if la acquire response es lost?); significantly higher latency; introduces un stateful session entre client y server que complicates horizontal scaling.
- **Por qué no**: Two round trips add 40-60ms a un 300ms budget. La acquire step solves un problem que does no exist if clients use transaction-scoped keys correctly.

### Opción C: No explicit idempotency key — use request hash
- **Ventajas**: Zero client change; server computes hash de request body como deduplication key.
- **Desventajas**: Hash collisions en semantically different requests con identical payloads (two transactions para la same customer a la same amount); request body may differ a través de retries if timestamps son included; does no handle partial failures where la client changes un non-key field en retry.
- **Por qué no**: Hash-based deduplication es fragile para financial transactions where two different transactions can have identical payloads.

### Opción D: Exactly-once via Kafka transactions (for async path)
- **Ventajas**: Kafka transactions provide exactly-once delivery semantics end-to-end sin application-level key management.
- **Desventajas**: Kafka exactly-once requires transactional producers y idempotent consumers wired together; does no address la HTTP synchronous path; adds Kafka transaction coordinator overhead (~2ms per produce); no applicable un la request-response pattern.
- **Por qué no**: La HTTP synchronous path requires un different mechanism. Kafka exactly-once es complementary para la async outbox relay, no un replacement.

## Consecuencias

### Positivo
- Duplicate detection es O(1) lookup por key en la idempotency store.
- Keys appear en structured logs, OTEL spans, y Kafka event headers — full auditability.
- Pattern es familiar un payment engineers; no explanation needed en code review.
- `InMemoryDecisionIdempotencyStore` en la PoC es replaceable con Redis `SET NX EX` en production sin changing la interface.

### Negativo
- Client contracts must document la key format y lifetime expectations; underdocumented clients will reuse keys incorrectly.
- La PoC Vert.x implementation has un known gap: la idempotency store es no yet implemented (`EvaluateRiskVerticle` does no perform key lookup), meaning la Karate `07_idempotency.feature` tests la HTTP contract pero no la deduplication logic.

### Mitigaciones
- Key format recommendation (`txn-{source}-{date}-{txnId}-{seq}`) es documented en la event envelope spec (doc 06).
- Vert.x gap es tracked en parity audit (doc 13) como un known implementation gap, no un design gap.
- Production migration: `InMemoryDecisionIdempotencyStore` → `RedisIdempotencyStore` con `SET NX EX {ttl}` es un one-class change behind la `DecisionIdempotencyStore` interface.

## Validación

- `tests/risk-engine-atdd/` includes idempotency scenario: same key twice returns identical response sin duplicate `DecisionEvaluated` event en outbox.
- `poc/java-vertx-distributed/atdd-tests/src/test/resources/features/07_idempotency.feature` defines la Karate contract.
- `InMemoryDecisionIdempotencyStore` uses `ConcurrentHashMap.putIfAbsent` — semantically correct para single-JVM scenarios.

## Relacionado

- [[0008-outbox-pattern-explicit]]
- [[0015-event-versioning-field]]
- Docs: doc 06 (`docs/06-eventos-versionados.md`)

## Referencias

- Stripe idempotency keys: https://stripe.com/docs/api/idempotent_requests
- doc 06: `docs/06-eventos-versionados.md`
