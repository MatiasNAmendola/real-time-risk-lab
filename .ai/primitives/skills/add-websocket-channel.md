---
name: add-websocket-channel
intent: Agregar un canal WebSocket bidireccional para evaluacion de riesgo interactiva
inputs: [path, message_schema_in, message_schema_out]
preconditions:
  - controller-app compila
  - EventBus Vert.x disponible
postconditions:
  - Endpoint WS acepta conexiones en el path indicado
  - Mensajes entrantes se procesan via use case
  - Respuestas se envian de vuelta al cliente
  - AsyncAPI actualizada con canal WS
  - ATDD: conectar, enviar 3 mensajes, recibir 3 respuestas
related_rules: [java-version, communication-patterns, observability-otel, testing-atdd]
---

# Skill: add-websocket-channel

## Pasos

1. **Upgrade HTTP a WS** en el Router:
   ```java
   router.get("/ws/risk").handler(ctx -> {
       if (ctx.request().headers().contains("Upgrade", "websocket", true)) {
           ctx.request().toWebSocket().onSuccess(ws -> handleWebSocket(ws));
       } else {
           ctx.response().setStatusCode(426).end("Upgrade Required");
       }
   });
   ```

2. **Implementar handler**:
   ```java
   void handleWebSocket(ServerWebSocket ws) {
       var correlationId = UUID.randomUUID().toString();
       ws.textMessageHandler(msg -> {
           var txRequest = Json.decodeValue(msg, TransactionRequest.class);
           useCase.evaluate(txRequest)
               .onSuccess(decision -> ws.writeTextMessage(Json.encode(decision)))
               .onFailure(err -> ws.writeTextMessage(errorJson(err, correlationId)));
       });
       ws.closeHandler(v -> log.info("WS closed correlationId={}", correlationId));
   }
   ```

3. **AsyncAPI** — agregar canal con protocol `ws`:
   ```yaml
   channels:
     /ws/risk:
       bindings:
         ws:
           method: GET
   ```

4. **ATDD**:
   ```gherkin
   Scenario: WebSocket evalua multiples transacciones
     Given the risk engine is running
     When I connect WebSocket to "/ws/risk"
     And I send transaction with amount 100000
     Then I receive a decision within 300ms
     And I send transaction with amount 1
     Then I receive a decision within 300ms
   ```

5. **Observabilidad**: log de cada mensaje con correlationId, span por mensaje procesado.

## Notas
- Limitar conexiones concurrentes (bulkhead): ver rule resilience-pattern.
- Timeout de inactividad: cerrar WS sin actividad en 60s.
- No procesar mensajes en paralelo sin control del orden de respuesta.
