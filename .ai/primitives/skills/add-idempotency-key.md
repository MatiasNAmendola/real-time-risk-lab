---
name: add-idempotency-key
intent: Agregar soporte de idempotencia a un use case o endpoint para tolerar retries sin duplicados
inputs: [use_case_name, storage_backend, ttl_seconds]
preconditions:
  - Use case a proteger identificado
  - Valkey 8 o Postgres disponible para almacenar keys procesadas
postconditions:
  - Requests duplicadas (mismo idempotencyKey) retornan la respuesta original cacheada
  - Primera ejecucion persiste resultado con TTL
  - ATDD verifica que el segundo request no ejecuta el use case nuevamente
related_rules: [events-versioning, java-version, error-handling]
---

# Skill: add-idempotency-key

## Pasos

1. **Puerto de salida** `domain/repository/IdempotencyStore.java`:
   ```java
   public interface IdempotencyStore {
       Future<Optional<String>> get(String key);
       Future<Void> put(String key, String result, Duration ttl);
   }
   ```

2. **Adapter en Valkey** `infrastructure/repository/IdempotencyStoreValkey.java`:
   ```java
   public Future<Optional<String>> get(String key) {
       return redis.get(Request.cmd(Command.GET).arg("idempotency:" + key))
           .map(r -> r == null ? Optional.empty() : Optional.of(r.toString()));
   }

   public Future<Void> put(String key, String result, Duration ttl) {
       return redis.send(Request.cmd(Command.SET)
           .arg("idempotency:" + key).arg(result)
           .arg("EX").arg(ttl.toSeconds()))
           .mapEmpty();
   }
   ```

3. **Wrappear use case**:
   ```java
   public Future<RiskDecision> evaluate(EvaluateTransactionRequest req) {
       var ikey = req.idempotencyKey();
       return store.get(ikey).compose(cached -> {
           if (cached.isPresent()) {
               log.info("idempotent hit key={}", ikey);
               return Future.succeededFuture(Json.decodeValue(cached.get(), RiskDecision.class));
           }
           return executeAndStore(req, ikey);
       });
   }
   ```

4. **Header en HTTP**: aceptar `Idempotency-Key` header en el endpoint. Si no viene, generar uno desde `transactionId`.

5. **ATDD**:
   ```gherkin
   Scenario: segundo request con mismo idempotencyKey retorna respuesta cacheada
     Given I POST to "/risk" with idempotencyKey "key-001"
     Then the decision is APPROVE
     When I POST to "/risk" again with idempotencyKey "key-001"
     Then the decision is APPROVE
     And the use case was called only once
   ```

## Notas
- TTL recomendado: 24h para decisiones de riesgo.
- Si el resultado aun no existe pero hay un request en progreso: retornar 409 Conflict (optimistic lock).
- idempotencyKey debe estar en el evento Kafka tambien (ver rule events-versioning).
