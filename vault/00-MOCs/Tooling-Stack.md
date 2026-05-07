---
title: Tooling Stack MOC
tags: [moc, tooling, versions]
created: 2026-05-07
---

# Tooling Stack MOC

## Lenguaje y runtime

| Tool | Versión | Por qué |
|------|---------|---------|
| Java | 25 LTS | Virtual Threads (Loom) estables, pattern matching, GCs nuevos |
| Go | 1.26+ | layout enterprise Go estilo reference codebase |

Ver [[0001-java-25-lts]], [[Virtual-Threads-Loom]].

## Frameworks

| Tool | Versión | Por qué |
|------|---------|---------|
| Vert.x | 5.x | reactivo, non-blocking, event bus, cluster-aware |
| Hazelcast | 5.x | cluster manager CP para el event bus de Vert.x |

Ver [[0003-vertx-for-distributed-poc]].

## Infraestructura

| Tool | Versión | Por qué |
|------|---------|---------|
| k3d | latest | k3s en Docker, k8s local rápido |
| OrbStack | latest | Docker/k8s nativo en Mac, más rápido que Docker Desktop |
| ArgoCD | 2.x | CD vía GitOps |
| Argo Rollouts | 1.x | canary deployments |
| Redpanda | latest | compatible con Kafka, single binary, sin ZooKeeper |

Ver [[0007-k3d-orbstack-switch]], [[k8s-local]].

## Observabilidad

| Tool | Por qué |
|------|---------|
| OpenObserve | backend OTEL liviano, self-hosted |
| OTel Java agent 2.x | auto-instrumentación zero-code |
| Prometheus + Grafana | kube-prom-stack, gates de canary |

Ver [[0004-openobserve-otel]], [[Observability]].

## Mocks AWS

| Tool | Reemplaza |
|------|-----------|
| Moto | Lambda, SQS, SNS, IAM |
| MinIO | S3 |
| ElasticMQ | SQS (nativo) |
| OpenBao | Secrets Manager / SSM |
| DynamoDB Local | DynamoDB |

Ver [[0005-aws-mocks-stack]].

## Testing

| Tool | Capa |
|------|------|
| Karate DSL | ATDD, Vert.x |
| Cucumber-JVM | ATDD, bare-javac |
| Bubble Tea | smoke runner TUI |
| testcontainers | integration |
| JaCoCo | cobertura |

Ver [[0006-atdd-karate-cucumber]], [[0009-bubbletea-tui-smoke]].

## Backlinks

[[Risk-Platform-Overview]] linkea acá como entry point de tooling.
