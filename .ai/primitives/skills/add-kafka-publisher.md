---
name: add-kafka-publisher
intent: Agregar publicacion de eventos de dominio a un topico Redpanda/Kafka
inputs: [topic_name, event_type, event_schema, key_field]
preconditions:
  - Redpanda corriendo (docker-compose o k8s-local)
  - KafkaProducer configurado en infrastructure/
postconditions:
  - Evento publicado al topico despues de cada decision relevante
  - Evento sigue el schema de events-versioning (eventId, correlationId, eventVersion, occurredAt, idempotencyKey)
  - Test de integracion verifica que el mensaje llega al topico
related_rules: [events-versioning, java-version, observability-otel, error-handling]
---

# Skill: add-kafka-publisher

## Pasos

1. **Definir schema del evento** siguiendo events-versioning:
   ```java
   record RiskDecisionEvent(
       String eventId,          // UUID v4
       String correlationId,
       String eventVersion,     // "1.0"
       Instant occurredAt,
       String idempotencyKey,
       String eventType,        // "RISK_DECISION"
       String transactionId,
       String decision,         // APPROVE / DECLINE / REVIEW
       String reason
   ) {}
   ```

2. **Puerto de salida** `domain/repository/EventPublisher.java`:
   ```java
   public interface EventPublisher {
       Future<Void> publish(String topic, String key, Object event);
   }
   ```

3. **Adapter** `infrastructure/publisher/KafkaEventPublisher.java`:
   - Usar `io.vertx.kafka.client.producer.KafkaProducer`.
   - Serializar con Jackson a JSON.
   - Key = `transactionId` (para ordering por transaccion).
   - En error: log + retorno de Future fallido (no swallow).

4. **Wiring en use case**: al completar la decision, llamar `eventPublisher.publish(...)`.
   - Usar outbox pattern si consistencia eventual requerida (ver skill add-outbox-event).

5. **Topico en Redpanda**: crear antes de producir:
   ```bash
   rpk topic create risk-decisions --partitions 6 --replication-factor 1
   ```

6. **Test de integracion**:
   - Consumir el topico con `KafkaConsumer` en el test.
   - Verificar que el mensaje llega dentro de 5s.
   - Verificar schema fields obligatorios.

## Notas
- `eventVersion` debe incrementarse si cambia el schema (no es opcional).
- No publicar datos PII en el topic sin encriptacion.
- Configurar `acks=all` para durabilidad en produccion.
