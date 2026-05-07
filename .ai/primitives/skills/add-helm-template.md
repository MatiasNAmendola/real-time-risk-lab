---
name: add-helm-template
intent: Agregar un nuevo recurso Kubernetes como template Helm al chart risk-engine en k8s-local
inputs: [resource_type, resource_name, values_keys]
preconditions:
  - poc/k8s-local/apps/risk-engine/Chart.yaml existe
  - k3d o OrbStack corriendo (para instalar)
postconditions:
  - Template YAML en poc/k8s-local/apps/risk-engine/templates/
  - Values documentados en values.yaml con defaults sensibles
  - helm lint pasa sin errores
  - helm template genera YAML valido
related_rules: [containers-docker, secrets-handling, naming-conventions]
---

# Skill: add-helm-template

## Pasos

1. **Crear template** en `poc/k8s-local/apps/risk-engine/templates/<resource>.yaml`:
   ```yaml
   {{- if .Values.prometheusRule.enabled }}
   apiVersion: monitoring.coreos.com/v1
   kind: PrometheusRule
   metadata:
     name: {{ include "risk-engine.fullname" . }}-slo
     labels:
       {{- include "risk-engine.labels" . | nindent 4 }}
   spec:
     groups:
       - name: risk-engine.slo
         rules:
           - alert: HighDecisionLatency
             expr: histogram_quantile(0.99, ...) > {{ .Values.prometheusRule.latencyP99Threshold }}
             for: 2m
   {{- end }}
   ```

2. **Agregar values** en `values.yaml`:
   ```yaml
   prometheusRule:
     enabled: true
     latencyP99Threshold: 0.3  # 300ms
   ```

3. **Validar**:
   ```bash
   helm lint poc/k8s-local/apps/risk-engine/
   helm template risk-engine poc/k8s-local/apps/risk-engine/ | kubectl apply --dry-run=client -f -
   ```

4. **Instalar/actualizar**:
   ```bash
   helm upgrade --install risk-engine poc/k8s-local/apps/risk-engine/ -n risk-engine
   ```

## Notas
- Usar `{{ include "risk-engine.labels" . | nindent 4 }}` para labels consistentes.
- Secrets via ExternalSecret, nunca en values.yaml (ver rule secrets-handling).
- Si el recurso es CRD de un addon (PrometheusRule, ExternalSecret), verificar que el addon este instalado primero.
