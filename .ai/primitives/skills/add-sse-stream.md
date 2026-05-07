---
name: add-sse-stream
intent: Agregar un endpoint Server-Sent Events para streaming de decisiones de riesgo en tiempo real
inputs: [path, event_type, payload_schema]
preconditions:
  - controller-app compila
  - EventBus de Vert.x disponible en el verticle
postconditions:
  - Endpoint GET devuelve Content-Type text/event-stream
  - Eventos publicados al EventBus se retransmiten al cliente
  - AsyncAPI spec actualizada con el canal SSE
  - Feature ATDD cubre conexion, recepcion de al menos 1 evento, y cierre limpio
related_rules: [java-version, communication-patterns, observability-otel, testing-atdd]
---

# Skill: add-sse-stream

## Pasos

1. **Registrar ruta SSE** en el Router:
   ```java
   router.get("/path/stream")
       .handler(SseHandler.create())
       .handler(this::handleStream);
   ```

2. **Implementar handler**:
   ```java
   void handleStream(RoutingContext ctx) {
       var correlationId = correlationId(ctx);
       var consumer = vertx.eventBus().<JsonObject>consumer("risk.decisions");
       consumer.handler(msg -> {
           ctx.response().write("event: risk-decision\n");
           ctx.response().write("data: " + msg.body().encode() + "\n\n");
       });
       ctx.response().closeHandler(v -> consumer.unregister());
   }
   ```

3. **Headers obligatorios** (Vert.x SSE handler los pone, verificar):
   - `Content-Type: text/event-stream`
   - `Cache-Control: no-cache`
   - `Connection: keep-alive`
   - `X-Correlation-Id: <correlationId>`

4. **Actualizar AsyncAPI** (`asyncapi.json`):
   - Canal: `risk/stream` con binding HTTP/SSE.
   - Message: schema del evento.

5. **ATDD feature** (`features/sse-stream.feature`):
   ```gherkin
   Scenario: SSE stream entrega eventos de riesgo
     Given the risk engine is running
     When I connect to GET "/risk/stream"
     And I POST a transaction to trigger a decision
     Then I receive an SSE event with type "risk-decision" within 5 seconds
     And the event payload contains "decision" and "correlationId"
   ```

6. **Span OTEL**: abrir span al conectar, cerrar al desconectar, log del count de eventos enviados.

## Notas
- Siempre desregistrar el consumer del EventBus en `closeHandler` para evitar memory leaks.
- En carga alta, usar backpressure: si el cliente no consume, cerrar la conexion con 503.
- No bloquear el event loop: todo debe ser reactive.
