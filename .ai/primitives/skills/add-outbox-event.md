---
name: add-outbox-event
intent: Implementar el Outbox Pattern para garantizar consistencia eventual entre decision persistida y evento publicado
inputs: [aggregate_name, event_type, outbox_table]
preconditions:
  - Postgres disponible
  - Kafka/Redpanda publisher configurado
postconditions:
  - Decision y evento outbox guardados en la misma transaccion Postgres
  - Relay asincrono publica el evento desde la tabla outbox
  - Evento eliminado de outbox solo despues de confirmacion del broker
  - ATDD verifica que el evento llega al topic incluso si el servicio reinicia entre decision y publicacion
related_rules: [events-versioning, java-version, error-handling, observability-otel]
---

# Skill: add-outbox-event

## Pasos

1. **Tabla outbox** en Postgres:
   ```sql
   CREATE TABLE outbox_events (
       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
       event_type VARCHAR(100) NOT NULL,
       payload JSONB NOT NULL,
       topic VARCHAR(200) NOT NULL,
       key VARCHAR(200),
       created_at TIMESTAMPTZ DEFAULT now(),
       processed_at TIMESTAMPTZ
   );
   CREATE INDEX ON outbox_events (processed_at) WHERE processed_at IS NULL;
   ```

2. **Guardar en la misma transaccion que la decision**:
   ```java
   pool.withTransaction(conn ->
       conn.preparedQuery("INSERT INTO risk_decisions ...").execute(decisionTuple)
           .compose(v -> conn.preparedQuery(
               "INSERT INTO outbox_events (event_type, payload, topic, key) VALUES ($1, $2, $3, $4)")
               .execute(Tuple.of(eventType, payload, topic, key)))
   );
   ```

3. **Relay** `infrastructure/outbox/OutboxRelay.java` — se ejecuta periodicamente:
   ```java
   vertx.setPeriodic(500, id -> {
       pool.preparedQuery("SELECT * FROM outbox_events WHERE processed_at IS NULL LIMIT 50 FOR UPDATE SKIP LOCKED")
           .execute()
           .compose(rows -> publishAll(rows))
           .compose(ids -> markProcessed(ids));
   });
   ```

4. **Marcar procesado** solo despues del ACK del broker.

5. **Monitoreo**: metrica `outbox.pending_count` (gauge) — si crece indefinidamente, hay un problema.

## Notas
- `FOR UPDATE SKIP LOCKED` evita que multiples instancias procesen el mismo evento.
- El relay puede correr en el mismo proceso o como pod separado.
- Si el relay falla: los eventos quedan en outbox y se reprocesaran. La idempotencia en el consumer evita duplicados.
