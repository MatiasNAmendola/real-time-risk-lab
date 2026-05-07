---
name: add-kafka-consumer
intent: Agregar un consumidor Kafka/Redpanda para procesar eventos de dominio de forma asincrona
inputs: [topic_name, consumer_group, event_schema, handler_use_case]
preconditions:
  - Redpanda corriendo
  - Topic existe
  - Use case handler existe en application/
postconditions:
  - Consumer registrado en consumer-app verticle
  - Mensajes procesados con idempotencia (via idempotencyKey)
  - Errores van a DLQ o log estructurado
  - ATDD verifica procesamiento end-to-end
related_rules: [events-versioning, java-version, error-handling, observability-otel]
---

# Skill: add-kafka-consumer

## Pasos

1. **Configurar consumer** en `infrastructure/consumer/<Topic>Consumer.java`:
   ```java
   KafkaConsumer<String, String> consumer = KafkaConsumer.create(vertx, config);
   consumer.subscribe(Set.of(TOPIC_NAME));
   consumer.handler(this::processRecord);
   ```

2. **Procesar con idempotencia**:
   ```java
   void processRecord(KafkaConsumerRecord<String, String> record) {
       var event = Json.decodeValue(record.value(), EventSchema.class);
       if (idempotencyStore.alreadyProcessed(event.idempotencyKey())) {
           log.info("duplicate event skipped key={}", event.idempotencyKey());
           return;
       }
       useCase.handle(event)
           .onSuccess(v -> {
               idempotencyStore.markProcessed(event.idempotencyKey());
               consumer.commit();
           })
           .onFailure(err -> handleConsumerError(record, err));
   }
   ```

3. **DLQ / Error handling**:
   - En error no recuperable: publicar mensaje a `<topic>-dlq`.
   - En error transitorio: no commitear offset (Kafka re-entrega).

4. **Span OTEL**: extraer trace context del header `traceparent` si existe.

5. **Shutdown graceful**: `consumer.close()` en verticle `stop()`.

6. **ATDD**:
   ```gherkin
   Scenario: consumer procesa evento del topico
     Given Redpanda is running with topic "risk-commands"
     When I produce a message to "risk-commands" with valid schema
     Then the consumer processes it within 5 seconds
     And the idempotencyKey is stored
     When I produce the same message again
     Then it is skipped as duplicate
   ```

## Notas
- `enable.auto.commit=false` siempre. Commit manual despues de procesamiento exitoso.
- Consumer group ID debe ser unico por instancia logica.
- En java-vertx-distributed: consumer-app es un pod separado con su propio consumer.
