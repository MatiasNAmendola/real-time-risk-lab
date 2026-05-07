# 17 — Decision: stack de observability local

## Fecha
2026-05-07

## Contexto
El cluster k3d/OrbStack inicialmente desplegaba kube-prometheus-stack (Prometheus + Grafana + Alertmanager + node-exporter + kube-state-metrics) ademas de OpenObserve.

## Problema
Consumo de RAM ~1-2 GB en local solo para observability, redundante con OpenObserve que ya cubre traces + logs + metrics + alerts.

## Decision
Remover kube-prometheus-stack. OpenObserve queda como la unica fuente de observability en local.

## Alternativas consideradas

### Opcion A: Solo OpenObserve (elegida)
- Pros: ~150 MB RAM, OTLP nativo, single binary, alerting built-in.
- Cons: pierde dashboards built-in de Grafana, Alertmanager routing avanzado.
- Why chosen: para PoC alcanza, para prod ajustamos.

### Opcion B: OpenObserve + Grafana standalone (sin Prometheus)
- Pros: visualizacion rica, queries Prom-compatible contra OpenObserve.
- Cons: hay que escribir dashboards a mano.
- Why not: extra complejidad sin retorno claro para una PoC.

### Opcion C: VictoriaMetrics + Grafana (sin OpenObserve)
- Pros: Prometheus drop-in mucho mas liviano.
- Cons: pierde traces + logs unificados.
- Why not: nos quedariamos sin trace store.

### Opcion D: Mantener kube-prometheus-stack
- Pros: stack estandar enterprise.
- Cons: ~1.5 GB RAM en local, redundante con OpenObserve.
- Why not: overkill para PoC.

## Consecuencias

### Positive
- Memoria libre para correr mas cosas.
- Una sola UI para observability.
- Demuestra criterio en NO over-engineerizar.

### Negative
- Argo Rollouts canary analysis no tiene provider OpenObserve nativo — usamos provider `web`
  con HTTP GET a la API SQL de OpenObserve. Las queries SQL son un placeholder que requiere
  verificacion contra la version real de OpenObserve en uso. Ver los TODO en
  poc/k8s-local/argocd/analysis-templates/success-rate.yaml y latency-p99.yaml.
- En produccion habra que migrar a Prometheus + Grafana cuando Alertmanager routing y
  recording rules de SLO valgan la pena.

### Mitigations
- Document as a design note: "chose minimum viable for exploration; in production I would upgrade to
  Prometheus + Grafana cuando Alertmanager + SLO recording rules valgan la pena".
- El archivo 30-kube-prom-stack-values.yaml.disabled conserva la config completa
  con instrucciones de restauracion en el header.

## Validation
- `kubectl get pods -A` post-cambio: no se ven pods con prefix `kube-prometheus`.
- OpenObserve sigue recibiendo OTLP de los services (verificable corriendo curl + abriendo OO UI).
- `bash -n poc/k8s-local/scripts/up.sh` parse sin errores.
- `cat dashboard/assets/config.yml | python3 -c 'import yaml,sys; yaml.safe_load(sys.stdin)'` valida.
- `cat dashboard/k8s/homer.yaml | python3 -c 'import yaml,sys; list(yaml.safe_load_all(sys.stdin))'` valida.

## Key Design Principle
"Elegi no agregar kube-prometheus-stack porque OpenObserve ya cubre traces + logs + metricas + alerting en un solo binario de ~150 MB. Para produccion es probable que lo cambie por el stack canonico Prometheus + Grafana, pero esa decision la tomo cuando los volumenes lo justifiquen, no por default."
