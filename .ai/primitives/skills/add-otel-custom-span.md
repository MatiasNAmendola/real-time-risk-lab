---
name: add-otel-custom-span
intent: Agregar un span OpenTelemetry custom para trazar una operacion de negocio especifica
inputs: [span_name, attributes, parent_context]
preconditions:
  - OpenTelemetry Java agent 2.x configurado (javaagent en JVM args)
  - OpenObserve o OTEL collector corriendo
postconditions:
  - Span visible en OpenObserve con nombre y atributos correctos
  - correlationId propagado como atributo del span
  - Span cerrado en finally o via try-with-resources
related_rules: [observability-otel, java-version]
---

# Skill: add-otel-custom-span

## Pasos

1. **Dependencia** en pom.xml (API, no SDK — el agent provee el SDK):
   ```xml
   <dependency>
     <groupId>io.opentelemetry</groupId>
     <artifactId>opentelemetry-api</artifactId>
     <version>1.44.0</version>
   </dependency>
   ```

2. **Obtener Tracer** (una vez por clase):
   ```java
   private static final Tracer tracer =
       GlobalOpenTelemetry.getTracer("com.naranjax.risk", "1.0.0");
   ```

3. **Crear span manual**:
   ```java
   Span span = tracer.spanBuilder("fraud.rule.evaluate")
       .setAttribute("rule.name", ruleName)
       .setAttribute("transaction.id", txId)
       .setAttribute("correlation.id", correlationId)
       .startSpan();
   try (Scope scope = span.makeCurrent()) {
       var result = ruleEngine.evaluate(ctx);
       span.setAttribute("rule.result", result.decision().name());
       return result;
   } catch (Exception e) {
       span.recordException(e);
       span.setStatus(StatusCode.ERROR, e.getMessage());
       throw e;
   } finally {
       span.end();
   }
   ```

4. **Propagar correlationId** como atributo baggage si va a otros servicios:
   ```java
   Baggage.current().toBuilder()
       .put("correlation.id", correlationId)
       .build()
       .makeCurrent();
   ```

5. **Verificar** en OpenObserve: buscar trace por `transaction.id` o `correlation.id`.

## Notas
- Nunca crear un Tracer global estatico compartido entre tests — puede causar conflictos.
- En Vert.x: propagar el contexto OTEL al cruzar verticle boundaries con `Context.current()`.
- El agent auto-instrumenta HTTP, JDBC, Kafka. Spans manuales son para logica de negocio custom.
