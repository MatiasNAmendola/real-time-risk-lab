# 03 — Roadmap de pruebas de concepto

## PoC 1 — Motor de decisión con budget de latencia

Objetivo: practicar camino crítico, timeouts, fallback y traza.

Incluido en `poc/java-risk-engine`.

Conceptos:

- `DecisionEngine` orquesta reglas + ML.
- `LatencyBudget` corta cuando el tiempo restante no alcanza.
- `CircuitBreaker` degrada si ML falla repetidamente.
- `DecisionTrace` registra por qué se decidió.
- `EventPublisher` simula eventos asincrónicos versionados.

## PoC 2 — Idempotencia

Incluido: `DecisionIdempotencyStore` + `InMemoryDecisionIdempotencyStore` evitan doble decisión/evento ante retries con la misma idempotency key.

## PoC 3 — Outbox pattern

Implementado. `DecisionOutbox` (port) + `InMemoryDecisionOutbox` + `OutboxRelay` background. El use case ya no espera al publisher: escribe al outbox y devuelve. El relay drena PENDING → PUBLISHED con reintentos. Test: `OutboxSmokeTest`.

Design note:

> El camino crítico no debería esperar a que un consumidor downstream confirme nada.

## PoC 4 — Benchmark simple

Implementado. `BenchmarkRunner` con virtual threads (disponibles desde Java 21), warmup, histograma p50/p95/p99/p999. Correr con `poc/java-risk-engine/scripts/benchmark.sh`.

Resultado de referencia (N=5000, 32 virtual threads):

- p50: 58 µs (decisiones por reglas, sin tocar ML)
- p95: 131 ms / p99: 156 ms (dominado por el fake ML que simula hasta 150 ms)
- throughput: 1.4k req/s
- fallbacks aplicados: 6.6%

Design note: el p99 del fake ML domina el budget. Es exactamente el síntoma del que hay que hablar — *"el modelo está en camino crítico y se come el budget; lo saco con timeout más agresivo, score cacheado o lo muevo a señal asincrónica"*.

## PoC 5 — Spring Boot real

Pending. Low ROI vs wiring cost for this exploration scope. Design rationale if defended in review: *"el dominio está aislado, sumar Spring es agregar adaptadores y un main; el motor no cambia"*.
