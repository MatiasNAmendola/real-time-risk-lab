---
name: events-versioning
applies_to: ["**/domain/event/**/*.java", "**/infrastructure/consumer/**/*.java", "**/infrastructure/publisher/**/*.java"]
priority: high
---

# Regla: events-versioning

## Schema obligatorio para todo evento de dominio

Todo evento publicado (Kafka, webhook, outbox) DEBE incluir estos campos:

```java
record DomainEvent<T>(
    String eventId,          // UUID v4, unico por evento
    String correlationId,    // ID de la transaccion que origino el evento
    String eventType,        // "RISK_DECISION", "FRAUD_ALERT", etc.
    String eventVersion,     // "1.0", "2.0" — incrementar si cambia el schema
    Instant occurredAt,      // timestamp de cuando ocurrio en el dominio
    String idempotencyKey,   // para consumidores: transactionId + eventType
    T payload                // datos especificos del evento
) {}
```

## Versionado

- Version `"1.0"` al crear el evento por primera vez.
- Si se agrega un campo opcional: version `"1.1"` (backward compatible).
- Si se elimina/renombra un campo o cambia un tipo: version `"2.0"` (breaking change).
- Al publicar version 2.0, publicar en paralelo a un nuevo topic o con schema registry migration.

## Inmutabilidad

- Los eventos son inmutables. Una vez publicados, no se modifican.
- Para corregir: publicar un nuevo evento compensatorio (ej. `RISK_DECISION_CORRECTED`).

## Consumidores

- Siempre validar `eventVersion` y manejar versiones desconocidas graciosamente (log + skip, no crash).
- Usar `idempotencyKey` para evitar reprocesamiento (ver skill add-idempotency-key).
- Extraer trace context del header Kafka `traceparent` si existe.

## Topic naming

- `risk-decisions` — decisiones del motor
- `fraud-alerts` — alertas de fraude detectado
- `<topic>-dlq` — dead letter queue para mensajes fallidos

## No permitido

- Eventos sin `eventId` o `correlationId`.
- Cambiar el schema de un evento existente sin incrementar `eventVersion`.
- Publicar datos PII sin encriptar en topics Kafka.
