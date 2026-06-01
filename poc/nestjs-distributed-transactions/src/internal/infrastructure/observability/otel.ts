import { NodeSDK } from '@opentelemetry/sdk-node';
import { getNodeAutoInstrumentations } from '@opentelemetry/auto-instrumentations-node';
import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-http';
import { OTLPMetricExporter } from '@opentelemetry/exporter-metrics-otlp-http';
import { PeriodicExportingMetricReader } from '@opentelemetry/sdk-metrics';

export function startOpenTelemetry(): NodeSDK | undefined {
  if (process.env.OTEL_ENABLED === 'false') return undefined;
  const sdk = new NodeSDK({
    traceExporter: new OTLPTraceExporter({ url: process.env.OTEL_EXPORTER_OTLP_TRACES_ENDPOINT ?? 'http://localhost:4318/v1/traces' }),
    metricReader: new PeriodicExportingMetricReader({ exporter: new OTLPMetricExporter({ url: process.env.OTEL_EXPORTER_OTLP_METRICS_ENDPOINT ?? 'http://localhost:4318/v1/metrics' }) }),
    instrumentations: [getNodeAutoInstrumentations()],
  });
  sdk.start();
  return sdk;
}
