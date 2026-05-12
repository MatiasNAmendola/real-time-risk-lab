---
adr: "0045"
title: Stack de observability local — OpenObserve como única fuente
status: accepted
date: 2026-05-07
tags: [decision/accepted, adr, observability, openobserve, prometheus, grafana, k8s]
source_archive: docs/17-decision-stack-observability-local.md (migrado 2026-05-12)
---

# ADR-0045: Stack de observability local — OpenObserve como única fuente

## Contexto

El cluster k3d/OrbStack inicialmente desplegaba kube-prometheus-stack (Prometheus + Grafana + Alertmanager + node-exporter + kube-state-metrics) además de OpenObserve.

El consumo de RAM era ~1-2 GB en local solo para observability, redundante con OpenObserve que ya cubre traces + logs + metrics + alerts.

## Decisión

Remover kube-prometheus-stack. OpenObserve queda como la única fuente de observability en local.

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
  `poc/k8s-local/argocd/analysis-templates/success-rate.yaml` y `latency-p99.yaml`.
- En produccion habra que migrar a Prometheus + Grafana cuando Alertmanager routing y
  recording rules de SLO valgan la pena.

### Mitigations
- Documento como design note: "chose minimum viable for exploration; in production I would upgrade to
  Prometheus + Grafana cuando Alertmanager + SLO recording rules valgan la pena".
- El archivo `30-kube-prom-stack-values.yaml.disabled` conserva la config completa
  con instrucciones de restauracion en el header.

## Validación
- `kubectl get pods -A` post-cambio: no se ven pods con prefix `kube-prometheus`.
- OpenObserve sigue recibiendo OTLP de los services (verificable corriendo curl + abriendo OO UI).
- `bash -n poc/k8s-local/scripts/up.sh` parse sin errores.
- `cat dashboard/assets/config.yml | python3 -c 'import yaml,sys; yaml.safe_load(sys.stdin)'` valida.
- `cat dashboard/k8s/homer.yaml | python3 -c 'import yaml,sys; list(yaml.safe_load_all(sys.stdin))'` valida.

## Principio de diseño clave

> "Elegi no agregar kube-prometheus-stack porque OpenObserve ya cubre traces + logs + metricas + alerting en un solo binario de ~150 MB. Para produccion es probable que lo cambie por el stack canonico Prometheus + Grafana, pero esa decision la tomo cuando los volumenes lo justifiquen, no por default."

## Relacionado

- [[0004-openobserve-otel]] — decisión original de adoptar OpenObserve.
- [[SLI-SLO-Error-Budget]] — los SLOs que este stack monitorea.
- [[Canary-Deployment]] — canary analysis que usa OpenObserve via provider `web`.
- [[Risk-Platform-Overview]]
