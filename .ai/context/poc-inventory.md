# PoC Inventory

| PoC | Que demuestra | Como correr | Stack | Estado |
|---|---|---|---|---|
| `poc/java-risk-engine` | Clean Architecture sin frameworks, Circuit Breaker, Idempotencia, Outbox, Virtual Threads, Correlation ID, benchmarks latencia | `./scripts/run.sh` luego `./scripts/test.sh` | Java 25, bare javac (sin Maven), JUnit 5 | Estable, demostrable |
| `poc/java-vertx-distributed` | Arquitectura distribuida layer-as-pod, Vert.x 5 + Hazelcast cluster, 4 modulos Maven separados, ATDD Karate | `docker-compose up -d && mvn package && mvn test -pl atdd-tests` | Java 25, Vert.x 5.0.12, Maven 3.9, Postgres 16, Valkey 8, Redpanda, Hazelcast, Karate 1.5+ | Estable, ATDD parcial |
| `poc/vertx-risk-platform` | Todos los patrones de comunicacion (REST/SSE/WS/Webhook/Kafka), OpenAPI, AsyncAPI, OTEL completo | `mvn package && ./scripts/run.sh` luego `cd cli/risk-smoke && go run .` | Java 25, Vert.x 5.0.12, Redpanda, Postgres 16, Valkey 8, otelcol 0.141.0, OpenObserve | En progreso (extensión comunicacion) |
| `poc/k8s-local` | Replica local de infra produccion: ArgoCD GitOps, Canary Argo Rollouts, SLO con Prometheus, External Secrets, AWS mocks | `./scripts/up.sh` luego `./scripts/status.sh` | k3d v5+/OrbStack, Helm 3, ArgoCD 9.2.4, Argo Rollouts 2.40.5, kube-prometheus 80.11.0, ESO 1.2.1, Redpanda, OpenObserve, Moto/MinIO/ElasticMQ/OpenBao | Estable |

## Detalle por PoC

### java-risk-engine

**Que demuestra**:
- "Puedo implementar Clean Architecture sin depender de frameworks"
- "Entiendo la diferencia entre puertos y adapters"
- "Tengo nocion de performance: p50 < 1ms, p99 < 5ms en la evaluacion de reglas puras"

**Comandos de demo**:
```bash
cd poc/java-risk-engine
./scripts/run.sh          # arranca el engine y muestra decisions
./scripts/bench.sh        # benchmark de latencia con virtual threads
```

### java-vertx-distributed

**Que demuestra**:
- "Cada capa del sistema puede escalar independientemente"
- "Uso Vert.x 5 reactive end-to-end sin bloquear el event loop"
- "ATDD con Karate sobre HTTP real"

**Comandos de demo**:
```bash
cd poc/java-vertx-distributed
docker-compose up -d
mvn package -DskipTests
# controller-app escucha en :8080
mvn test -pl atdd-tests  # ATDD en verde
```

### vertx-risk-platform

**Que demuestra**:
- "Implemento todos los patrones de comunicacion (REST/SSE/WS/Webhook/Kafka) en la misma plataforma"
- "OpenAPI + AsyncAPI como contratos formales"
- "OTEL end-to-end: trace desde HTTP hasta Kafka"

**Comando de demo**:
```bash
cd poc/vertx-risk-platform
mvn package && ./scripts/run.sh
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
| `tests/risk-engine-atdd` | Cucumber-JVM 7 ATDD sobre API HTTP | `mvn test` (requiere app en :8080) | En progreso |
| `cli/risk-smoke` | Go + Bubble Tea TUI con 9 smoke checks | `go run .` (requiere app en :8080) | En progreso |
