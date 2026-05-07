---
name: add-resilience-pattern
intent: Agregar patron de resiliencia (circuit breaker, bulkhead, retry, timeout) a una llamada de infraestructura
inputs: [pattern_type, target_service, failure_threshold, timeout_ms]
preconditions:
  - Llamada a servicio externo identificada en infrastructure/
postconditions:
  - Patron implementado envolviendo la llamada
  - Fallback definido
  - Metrica OTEL registrada en cada estado (open/closed/half-open para CB)
  - Test verifica comportamiento bajo falla simulada
related_rules: [java-version, observability-otel, error-handling, architecture-clean]
---

# Skill: add-resilience-pattern

## Circuit Breaker (Vert.x Circuit Breaker)

```java
CircuitBreaker cb = CircuitBreaker.create("ml-service-cb", vertx,
    new CircuitBreakerOptions()
        .setMaxFailures(5)
        .setTimeout(200)                // timeout por llamada (ms)
        .setFallbackOnFailure(true)
        .setResetTimeout(30_000));      // tiempo en OPEN antes de HALF-OPEN

cb.execute(promise -> {
    mlClient.score(ctx).onComplete(promise);
}).recover(err -> {
    log.warn("CB open, using fallback rule. correlationId={}", correlationId);
    metricsCounter.add(1, Attributes.of(AttributeKey.stringKey("cb.state"), "open"));
    return Future.succeededFuture(FallbackDecision.RULE_BASED);
});
```

## Bulkhead (semaforo con limite de concurrencia)

```java
Semaphore bulkhead = new Semaphore(50); // max 50 evaluaciones concurrentes

Future<RiskDecision> evaluate(TransactionContext ctx) {
    if (!bulkhead.tryAcquire()) {
        return Future.failedFuture(new BulkheadException("Too many concurrent evaluations"));
    }
    return useCase.evaluate(ctx)
        .onComplete(v -> bulkhead.release());
}
```

## Retry con backoff exponencial

```java
RetryPolicy policy = RetryPolicy.builder()
    .withMaxAttempts(3)
    .withBackoff(100, 1000, ChronoUnit.MILLIS)
    .build();

Failsafe.with(policy).getStageAsync(() -> externalCall());
```
O con Vert.x puro:
```java
retryWithBackoff(attempt -> externalCall(), 3, 100, 2.0);
```

## Timeout

```java
useCase.evaluate(ctx)
    .timeout(300)  // Vert.x Future.timeout en ms
    .onFailure(err -> {
        if (err instanceof TimeoutException) {
            log.warn("evaluation timeout correlationId={}", correlationId);
        }
    });
```

## Notas
- Patron de preferencia para llamadas ML: Circuit Breaker + Timeout.
- Para Postgres/Valkey: Timeout + Retry (1 reintento).
- Nunca usar `Thread.sleep` para backoff en Vert.x (bloquea el event loop).
