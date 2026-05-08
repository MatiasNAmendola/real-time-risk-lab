---
name: add-rest-endpoint
intent: Agregar un nuevo endpoint REST a controller-app respetando OpenAPI y Clean Architecture
inputs: [path, http_method, request_schema, response_schema, use_case_name]
preconditions:
  - poc/vertx-layer-as-pod-eventbus o poc/vertx-layer-as-pod-http compila (./gradlew shadowJar)
  - El use case correspondiente existe en application/usecase/
postconditions:
  - Endpoint registrado en el Router de Vert.x
  - OpenAPI spec actualizada (openapi.json o openapi.yaml)
  - Feature ATDD agregada en atdd-tests/src/test/resources/features/
  - Test corre en verde
related_rules: [java-version, architecture-clean, testing-atdd, observability-otel, communication-patterns]
---

# Skill: add-rest-endpoint

## Pasos

1. **Definir el contrato** en `src/main/resources/openapi.yaml` (o `.json`):
   - Agregar path, method, requestBody (si aplica), responses.
   - Incluir `operationId` en camelCase.
   - Referenciar schemas en `components/schemas`.

2. **Crear o reutilizar DTO** en `application/dto/<aggregate>/`:
   - `<Entity>Request.java` (record o clase inmutable).
   - `<Entity>Response.java`.
   - Usar Jackson annotations si necesario (`@JsonProperty`).

3. **Crear o reutilizar Mapper** en `application/mapper/`:
   - `<Entity>Mapper.java` — convierte Request -> dominio, dominio -> Response.
   - Sin lógica de negocio en el mapper.

4. **Agregar handler** en `infrastructure/controller/<Aggregate>Handler.java`:
   ```java
   void handlePost<Entity>(RoutingContext ctx) {
       var body = ctx.body().asJsonObject();
       var request = mapper.toRequest(body);
       useCase.execute(request)
           .onSuccess(result -> ctx.response()
               .setStatusCode(201)
               .putHeader("Content-Type", "application/json")
               .end(Json.encode(mapper.toResponse(result))))
           .onFailure(err -> handleError(ctx, err));
   }
   ```

5. **Registrar ruta** en `infrastructure/controller/<Aggregate>Router.java` o equivalente:
   ```java
   router.post("/path").handler(handler::handlePost<Entity>);
   ```

6. **Agregar span OTEL** (ver rule observability-otel):
   - `tracer.spanBuilder("http.post.<entity>").startSpan()`
   - Propagar `correlationId` en MDC y en response header `X-Correlation-Id`.

7. **Feature ATDD** en `atdd-tests/src/test/resources/features/<entity>.feature`:
   ```gherkin
   Feature: POST /<path>
     Scenario: happy path
       Given the risk engine is running
       When I POST to "/<path>" with body ...
       Then I receive status 201
       And the response contains ...
   ```

8. **Verificar**: `./gradlew :poc:vertx-layer-as-pod-eventbus:atdd-tests:test -Patdd` debe pasar en verde.

## Notas
- No introducir lógica de negocio en el controller/handler.
- Errores de negocio -> 422 Unprocessable Entity con body `{ "error": "...", "correlationId": "..." }`.
- Errores inesperados -> 500 con `correlationId` para trazabilidad.
