---
name: add-otel-custom-metric
intent: Agregar una metrica custom OpenTelemetry (counter, histogram o gauge) para observabilidad de negocio
inputs: [metric_name, metric_type, unit, description, attributes]
preconditions:
  - opentelemetry-api en classpath
  - OTEL exporter configurado (agent o SDK manual)
postconditions:
  - Metrica exportada al collector OTEL
  - Visible en OpenObserve o Prometheus (via otelcol scrape)
  - ServiceMonitor o Prometheus scrape config actualizado si aplica
related_rules: [observability-otel, java-version]
---

# Skill: add-otel-custom-metric

## Pasos

1. **Obtener Meter** (una vez por clase):
   ```java
   private static final Meter meter =
       GlobalOpenTelemetry.getMeter("io.riskplatform.risk");
   ```

2. **Counter** (para eventos que se acumulan):
   ```java
   private static final LongCounter decisionsCounter = meter
       .counterBuilder("risk.decisions.total")
       .setDescription("Total risk decisions by outcome")
       .setUnit("{decisions}")
       .build();

   // Usar:
   decisionsCounter.add(1, Attributes.of(
       AttributeKey.stringKey("decision"), decision.name(),
       AttributeKey.stringKey("rule"), triggeredRule
   ));
   ```

3. **Histogram** (para latencias):
   ```java
   private static final DoubleHistogram evaluationLatency = meter
       .histogramBuilder("risk.evaluation.duration")
       .setDescription("Time to evaluate a risk decision")
       .setUnit("s")
       .setExplicitBucketBoundariesAdvice(List.of(0.01, 0.05, 0.1, 0.2, 0.3, 0.5, 1.0))
       .build();

   // Usar:
   long start = System.nanoTime();
   var result = evaluate(ctx);
   evaluationLatency.record((System.nanoTime() - start) / 1e9,
       Attributes.of(AttributeKey.stringKey("outcome"), result.decision().name()));
   ```

4. **Gauge** (para valores instantaneos):
   ```java
   meter.gaugeBuilder("risk.active_evaluations")
       .setDescription("Current evaluations in flight")
       .buildWithCallback(measurement ->
           measurement.record(activeCount.get()));
   ```

5. **Verificar**: en OpenObserve buscar la metrica por nombre. En Prometheus: `{__name__=~"risk_.*"}`.

## Notas
- Nombres en snake_case con prefijo `risk.` para el dominio de este proyecto.
- Usar `{unit}` UCUM para unidades (s, ms, {requests}, By, etc.).
- Buckets de histograma deben ser apropiados para el SLO: si el SLO es p99 < 300ms, incluir 0.3 como bucket.
