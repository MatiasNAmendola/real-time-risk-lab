---
name: add-prometheus-rule
intent: Agregar una PrometheusRule con alertas de SLI/SLO para el risk engine
inputs: [rule_name, metric_expr, threshold, severity, for_duration]
preconditions:
  - kube-prometheus-stack instalado en el cluster
  - ServiceMonitor del risk engine configurado
postconditions:
  - PrometheusRule CRD aplicado en namespace risk-engine
  - Alerta visible en Prometheus Alerts UI
  - Descripcion y runbook en annotations
related_rules: [observability-otel, containers-docker]
---

# Skill: add-prometheus-rule

## Pasos

1. **Crear o actualizar** `poc/k8s-local/apps/risk-engine/templates/prometheus-rule.yaml`:
   ```yaml
   apiVersion: monitoring.coreos.com/v1
   kind: PrometheusRule
   metadata:
     name: risk-engine-slo
     labels:
       release: kube-prometheus-stack  # label requerido por el operator
   spec:
     groups:
       - name: risk-engine.sli
         interval: 30s
         rules:
           - record: risk_engine:request_success_rate:5m
             expr: |
               rate(http_server_requests_total{status=~"2.."}[5m])
               / rate(http_server_requests_total[5m])

       - name: risk-engine.slo.alerts
         rules:
           - alert: RiskEngineHighLatencyP99
             expr: histogram_quantile(0.99, rate(http_server_request_duration_seconds_bucket[5m])) > 0.3
             for: 2m
             labels:
               severity: warning
             annotations:
               summary: "p99 latency > 300ms"
               description: "Risk engine p99 latency is {{ $value | humanizeDuration }}"
               runbook_url: "https://github.com/.../runbook.md"
   ```

2. **Verificar** que Prometheus la recoge:
   ```bash
   kubectl port-forward svc/kube-prometheus-stack-prometheus 9090 -n monitoring
   # Ir a localhost:9090/rules y buscar risk-engine
   ```

3. **Para SLO de error rate**:
   ```yaml
   - alert: RiskEngineHighErrorRate
     expr: risk_engine:request_success_rate:5m < 0.99
     for: 5m
     labels:
       severity: critical
   ```

## Notas
- La label `release: kube-prometheus-stack` es requerida para que el Prometheus Operator recoja el recurso.
- Mantener alertas con runbook_url aunque sea placeholder.
- SLO target: p99 < 300ms, success rate > 99%.
