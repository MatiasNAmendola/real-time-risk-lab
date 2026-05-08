---
adr: "0040"
title: k6 (Grafana) para load testing HTTP, JMH para in-process
status: accepted
date: 2026-05-07
tags: [decision/accepted, bench, observability, slo]
---

# ADR-0040: k6 para load testing HTTP

## Contexto

`bench/scripts/competition.sh` y el módulo `bench/distributed-bench` (Java) generaban carga HTTP a los cuatro PoCs con un thread pool hand-rolled. Funcionaba pero arrastraba problemas: percentiles calculados sobre un array bounded (sesgo en long-tail), sin ramps nativos (concurrencia fija), sin export estructurado a Prometheus, requiere `./gradlew shadowJar` + JVM warmup antes de cada corrida, y reinventa scenarios (smoke, load, stress, spike, soak) que cualquier herramienta industry-standard ya trae.

## Decisión

Adoptar **k6** (Grafana Labs, AGPLv3, single binary Go) como herramienta de load testing HTTP. Mantener JMH (`bench/inprocess-bench`) para microbenchmarks in-process (sin red, sin serialización), donde JMH sigue siendo state-of-the-art.

- Scripts JS reusables en `bench/k6/scenarios/{smoke,load,stress,spike,soak}.js`.
- Library compartida en `bench/k6/lib/{config,payload,thresholds}.js`.
- Wire al CLI: `./nx bench k6 <scenario> [--target SVC]` y `./nx bench k6 competition <scenario>`.
- Export a OpenObserve vía Prometheus remote-write (`-o experimental-prometheus-rw`).
- Profiles por servicio en `bench/k6/profiles/*.json`.

## Consecuencias

- **Ventajas**:
  - Percentiles HDR-style sobre todo el dataset (no array bounded).
  - Ramps nativos (`stages: [{duration, target}]`) → spike/stress/soak triviales.
  - Threshold gating (`http_req_duration: ['p(99)<300']`) con exit code 99 en fail → CI-friendly.
  - Output múltiple en una corrida: stdout summary + JSON + Prometheus RW + CSV.
  - Single static binary, install vía `brew install k6`, sin JVM warmup.
  - Industry-standard (Grafana Labs, k6 Cloud) → lenguaje común con SREs.
- **Desventajas**:
  - Scripts en JS (Goja runtime) → otra runtime para mantener.
  - AGPLv3 para el binario (uso interno está OK; no se redistribuye).
  - Para escenarios > 10k VUs hay que ir a k6 Operator en k8s (fuera de scope acá).
- **Mitigaciones**:
  - JMH (`bench/inprocess-bench`) se mantiene para micro-bench in-process.
  - El custom Java bench (`bench/distributed-bench`) queda deprecado pero no se borra hasta validar k6 contra los 4 servicios en CI.

## Alternativas consideradas

- **Mantener custom Java bench**: rechazado por costo de mantener percentiles correctos y ramps.
- **wrk / wrk2**: descartado, no scriptable más allá de Lua, sin Prometheus RW.
- **Gatling**: descartado, JVM start-up + DSL Scala/Java, peor DX que k6.
- **JMeter**: descartado, XML-driven, GUI-first, pesado.
- **Locust**: considerado, pero Python con asyncio escala peor que k6 a 200+ VUs single-node.

## Relacionado

- [[0004-openobserve-otel]] — k6 pushea a OpenObserve vía Prometheus RW.
- [[0023-smoke-runner-asymmetric]] — el smoke de Bubbletea sigue siendo para verificación funcional; k6 es para SLO/perf.
- [[bench/k6/README]] — guía operativa.
