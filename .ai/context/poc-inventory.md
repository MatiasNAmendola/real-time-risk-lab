# PoC Inventory

| PoC | Que demuestra | Como correr | Stack | Estado |
|---|---|---|---|---|
| `poc/no-vertx-clean-engine` | Clean Architecture sin frameworks, Circuit Breaker, Idempotencia, Outbox, Virtual Threads, Correlation ID, benchmarks latencia | `./scripts/run.sh` luego `./scripts/test.sh` | Java 21 LTS baseline, bare javac (sin Gradle), JUnit 5; Java 25 objetivo documentado | Estable, demostrable |
| `poc/vertx-layer-as-pod-eventbus` | Arquitectura distribuida layer-as-pod, Vert.x 5 + Hazelcast cluster, 4 modulos Gradle separados, ATDD Karate | `docker-compose up -d && ./gradlew shadowJar && ./gradlew :poc:vertx-layer-as-pod-eventbus:atdd-tests:test -Patdd` | Java 21 LTS baseline, Vert.x 5.0.12, Gradle, Postgres 16, Valkey 8, Redpanda, Hazelcast, Karate 1.5+; Java 25 objetivo documentado | Estable, ATDD parcial |
| `poc/vertx-service-mesh-bounded-contexts` | Service-to-service real entre bounded contexts: risk-decision, fraud-rules, ml-scorer y audit via Vert.x EventBus RPC/async | `./scripts/up.sh && ./scripts/demo.sh` | Java 21 baseline, Vert.x 5.0.12, Hazelcast EventBus cluster, Docker Compose | En progreso |
| `poc/vertx-layer-as-pod-http` | Todos los patrones de comunicacion (REST/SSE/WS/Webhook/Kafka), OpenAPI, AsyncAPI, OTEL completo | `./gradlew shadowJar && ./scripts/run.sh` luego `cd cli/risk-smoke && go run .` | Java 21 LTS baseline, Vert.x 5.0.12, Redpanda, Postgres 16, Valkey 8, otelcol 0.141.0, OpenObserve; Java 25 objetivo documentado | En progreso (extensión comunicacion) |
| `poc/k8s-local` | Replica local de infra produccion: ArgoCD GitOps, Canary Argo Rollouts, SLO con Prometheus, External Secrets, AWS mocks | `./scripts/up.sh` luego `./scripts/status.sh` | k3d v5+/OrbStack, Helm 3, ArgoCD 9.2.4, Argo Rollouts 2.40.5, kube-prometheus 80.11.0, ESO 1.2.1, Redpanda, OpenObserve, Moto/MinIO/ElasticMQ/OpenBao | Estable |

## Detalle por PoC

### no-vertx-clean-engine

**Que demuestra**:
- "Puedo implementar Clean Architecture sin depender de frameworks"
- "Entiendo la diferencia entre puertos y adapters"
- "Tengo nocion de performance: p50 < 1ms, p99 < 5ms en la evaluacion de reglas puras"

**Comandos de demo**:
```bash
cd poc/no-vertx-clean-engine
./scripts/run.sh          # arranca el engine y muestra decisions
./scripts/bench.sh        # benchmark de latencia con virtual threads
```

### vertx-layer-as-pod-eventbus

**Que demuestra**:
- "Cada capa del sistema puede escalar independientemente"
- "Uso Vert.x 5 reactive end-to-end sin bloquear el event loop"
- "ATDD con Karate sobre HTTP real"

**Comandos de demo**:
```bash
cd poc/vertx-layer-as-pod-eventbus
docker-compose up -d
./gradlew shadowJar
# controller-app escucha en :8080
./gradlew :poc:vertx-layer-as-pod-eventbus:atdd-tests:test -Patdd  # ATDD en verde
```

### vertx-service-mesh-bounded-contexts

PoC dedicado a service-to-service real: cuatro bounded contexts independientes se comunican por Vert.x EventBus. Se usa para explicar que `vertx-layer-as-pod-eventbus` es layer-as-pod, no microservicios colaborando.

```bash
cd poc/vertx-service-mesh-bounded-contexts
./scripts/up.sh
./scripts/demo.sh
./scripts/down.sh
```

### vertx-layer-as-pod-http

**Que demuestra**:
- "Implemento todos los patrones de comunicacion (REST/SSE/WS/Webhook/Kafka) en la misma plataforma"
- "OpenAPI + AsyncAPI como contratos formales"
- "OTEL end-to-end: trace desde HTTP hasta Kafka"

**Comando de demo**:
```bash
cd poc/vertx-layer-as-pod-http
./gradlew shadowJar && ./scripts/run.sh
# En otra terminal:
cd cli/risk-smoke && go run .  # TUI con 9 checks
```

### k8s-local

**Que demuestra**:
- "Demuestra patrones de deployment: ArgoCD GitOps, canary, SLO"
- "Puedo operar la infra de observabilidad: Prometheus + OpenObserve"
- "External Secrets: no hay secrets hardcodeados"

**Comandos de demo**:
```bash
cd poc/k8s-local
./scripts/up.sh                    # levanta todo (~3min)
./scripts/status.sh                # verifica estado
kubectl argo rollouts get rollout risk-engine -n risk-engine  # canary
```

## Tests / CLI

| Componente | Que es | Como correr | Estado |
|---|---|---|---|
| `tests/risk-engine-atdd` | Cucumber-JVM 7 ATDD sobre API HTTP | `./gradlew test` (requiere app en :8080) | En progreso |
| `cli/risk-smoke` | Go + Bubble Tea TUI con 9 smoke checks | `go run .` (requiere app en :8080) | En progreso |
