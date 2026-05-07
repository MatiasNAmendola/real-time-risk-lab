---
name: observability-otel
applies_to: ["**/*.java", "**/docker-compose*.yml", "**/values*.yaml"]
priority: high
---

# Regla: observability-otel

## Tres pilares obligatorios

Todo request procesado por la aplicacion DEBE producir:
1. **Trace**: span con correlationId, duration, status.
2. **Log**: linea estructurada JSON con correlationId, traceId, spanId.
3. **Metric**: al menos `http.server.request.duration` (auto) y metrica de negocio (manual).

## correlationId

- Generar en el boundary de entrada (HTTP handler, Kafka consumer).
- Propagar via MDC: `MDC.put("correlationId", id)`.
- Incluir en toda respuesta HTTP: header `X-Correlation-Id`.
- Incluir en eventos Kafka: campo `correlationId` en el payload.
- Limpiar MDC al final del request: `MDC.clear()` en `finallyHandler`.

## Configuracion del agente OTEL

```bash
# En JVM args:
-javaagent:opentelemetry-javaagent.jar
-Dotel.service.name=risk-engine
-Dotel.exporter.otlp.endpoint=http://otel-collector:4317
-Dotel.resource.attributes=deployment.environment=local,service.version=1.0.0
```

## Spans manuales

Solo para logica de negocio no instrumentada automaticamente:
- Motor de reglas: `fraud.rules.evaluate`
- ML scoring: `ml.model.score`
- Outbox relay: `outbox.relay.dispatch`

Ver skill `add-otel-custom-span`.

## Metricas de negocio obligatorias

- `risk.decisions.total` (counter) con atributo `decision` (APPROVE/DECLINE/REVIEW).
- `risk.evaluation.duration` (histogram) en segundos.
- `risk.rules.fired.total` (counter) con atributo `rule_name`.

Ver skill `add-otel-custom-metric`.

## Stack local

- Collector: `otelcol-contrib 0.141.0` (docker-compose o k8s).
- Backend: OpenObserve (standalone) — no Jaeger, no Zipkin.
- Metricas: exportar a Prometheus endpoint `/metrics` O a OTLP.

## No permitido

- `System.out.println` para debugging. Usar SLF4J/Logback.
- Logs sin correlationId en paths de request.
- Swallow exceptions sin loguear.
