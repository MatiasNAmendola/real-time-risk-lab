# PoC Java — Risk Decision Engine con Clean Architecture

PoC sin dependencias externas para ejercitar los conceptos clave del dominio:

- Camino crítico de decisión.
- Clean / Hexagonal Architecture.
- Puertos de entrada y salida.
- Timeouts por budget.
- Circuit breaker.
- Fallback de ML.
- Idempotencia ante retries.
- Eventos versionados asincrónicos.
- Correlation ID y traza auditable.
- DTOs y mapper explícitos.
- Outbox pattern con relay asincrónico.
- Repository concreto para decisiones.
- TransactionManager concreto.
- ExecutionContext + logging estructurado.

## Java

El código compila con baseline Java 21 LTS (`--release 21`) para mantener compatibilidad de tooling. Puede ejecutarse sobre JDK 21+; Java 25 LTS queda como objetivo documentado.

El baseline ejecutable de esta PoC es Java 21 LTS por compatibilidad del tooling. Java 25 LTS queda documentado como objetivo de runtime moderno cuando el ecosistema soporte el cambio sin fricción.

## Ejecutar

```bash
./scripts/run.sh
```

## Test smoke

```bash
./scripts/test.sh
```

## Benchmark

Ejecuta un microbenchmark de latencia usando virtual threads (disponibles desde Java 21).

```bash
# Defaults: N=5000 requests, M=32 concurrency, warmup=500
./scripts/benchmark.sh

# Custom args
./scripts/benchmark.sh 10000 64 1000
```

El script compila todo desde cero, hace warmup de JVM (configurable) y luego mide N requests con M virtual threads concurrentes. Cada request usa un `IdempotencyKey` único para evitar cache hits y forzar el camino crítico completo.

### Ejemplo de output (N=5000, M=32, warmup=500, MacBook M-series)

```
=== Risk Engine Benchmark ===
requests=5000  concurrency=32  warmup=500

Warming up... done.
Measuring...

─────────────────────────────────────────────
  Total wall time  : 3,272.9 ms
  Throughput       : 1,528 req/s
─────────────────────────────────────────────
  p50              : 50.08 µs
  p95              : 127.20 ms
  p99              : 153.63 ms
  p99.9            : 159.49 ms
  max              : 161.36 ms
  min              : 1.75 µs
─────────────────────────────────────────────
  Decisions:
    APPROVE    : 1,062  (21.2 %)
    DECLINE    : 0  (0.0 %)
    REVIEW     : 3,938  (78.8 %)
  Fallbacks applied: 377  (7.5 %)
─────────────────────────────────────────────
```

**Nota**: el p95/p99 alto se explica porque `FakeRiskModelScorer` introduce un sleep aleatorio de hasta 150 ms para simular latencia de modelo ML. El p50 (~50 µs) representa las decisiones tomadas por reglas antes de invocar el modelo.

## Estructura

Layout alineado al layout canónico de servicios Go enterprise.

```text
src/main/java/io/riskplatform/engine
├── cmd/                                   # cmd/main.go en mega — entry point
│   └── RiskApplication.java               # delega a CliRunner
├── config/                                # wiring manual de dependencias
│   └── RiskApplicationFactory.java        # equivalente a cmd/main.go (bootstrap) en mega
├── domain/
│   ├── entity/                            # internal/domain/entities/ en mega — VOs y entidades
│   ├── repository/                        # internal/domain/repositories/ en mega — port out interfaces
│   │   ├── ClockPort.java
│   │   ├── DecisionEventPublisher.java
│   │   ├── DecisionIdempotencyStore.java
│   │   ├── FeatureProvider.java
│   │   ├── OutboxRepository.java
│   │   ├── RiskDecisionRepository.java
│   │   └── RiskModelScorer.java
│   ├── usecase/                           # internal/domain/usecases/ en mega — port in interfaces
│   │   └── EvaluateRiskUseCase.java
│   ├── service/                           # lógica de dominio pura (policies)
│   └── rule/                              # reglas de fraude
├── application/
│   ├── usecase/
│   │   └── risk/                          # internal/application/usecases/ en mega — impls por agregado
│   │       ├── EvaluateTransactionRiskService.java
│   │       └── LatencyBudget.java
│   ├── mapper/                            # internal/application/mappers/ en mega
│   │   └── RiskDecisionMapper.java
│   ├── dto/                               # DTOs de entrada/salida
│   └── common/                            # ExecutionContext, StructuredLogger, TransactionManager
└── infrastructure/
    ├── controller/                        # internal/infrastructure/controllers/ en mega — inbound CLI
    │   ├── CliRunner.java
    │   └── BenchmarkRunner.java
    ├── consumer/                          # internal/infrastructure/consumers/ en mega — inbound async
    │   └── ConsumerPlaceholder.java       # vacío: aquí irían @KafkaListener cuando usemos Tansu
    ├── repository/                        # internal/infrastructure/repositories/ en mega — outbound impls
    │   ├── event/                         # outbox + publisher
    │   ├── feature/                       # feature provider in-memory
    │   ├── idempotency/                   # idempotency store in-memory
    │   ├── log/                           # ConsoleStructuredLogger
    │   ├── ml/                            # FakeRiskModelScorer
    │   └── persistence/                   # RiskDecision + TransactionManager in-memory
    ├── resilience/                        # CircuitBreaker
    └── time/                              # SystemClockAdapter
```

## HTTP API

### Arrancar el servidor HTTP

```bash
./scripts/run-http.sh
# Puerto configurable via env:
RISK_HTTP_PORT=9090 ./scripts/run-http.sh
```

Puerto por defecto: **8081** (distinto del PoC Vert.x distributed que usa 8080, para que ambos puedan correr a la vez).

### Endpoints

| Method | Path      | Descripción                       |
|--------|-----------|-----------------------------------|
| GET    | /healthz  | Liveness probe                    |
| GET    | /readyz   | Readiness probe                   |
| POST   | /risk     | Evalúa una transacción            |
| GET    | /         | Mensaje de bienvenida             |

#### Ejemplo POST /risk

```bash
curl -s -X POST http://localhost:8081/risk \
  -H 'Content-Type: application/json' \
  -d '{"transactionId":"tx-1","customerId":"c-1","amountCents":1000}'
```

Respuesta:

```json
{"decision":"APPROVE","reason":"score below threshold","correlationId":"<uuid>","traceId":"<uuid>"}
```

Monto alto (REVIEW/DECLINE):

```bash
curl -s -X POST http://localhost:8081/risk \
  -H 'Content-Type: application/json' \
  -d '{"transactionId":"tx-2","customerId":"c-2","amountCents":100000}'
```

Campos opcionales del body: `correlationId` (si no viene, se genera automáticamente), `idempotencyKey`.
Tambien se acepta `X-Correlation-Id` como header HTTP.

### Por que stdlib y no framework

Este PoC demuestra que Clean Architecture no requiere un framework HTTP. El `HttpController` es un adapter más — equivalente al `CliRunner` y `BenchmarkRunner` que ya existen. Reemplazarlo por Spring Boot o Vert.x es un swap de adapter, no una reescritura: el dominio, los casos de uso, y las reglas de negocio quedan intactos.

### Diferencia con el PoC Vert.x distributed

El PoC Vert.x corre en N JVMs separadas con comunicación vía event bus. Este PoC corre en **1 JVM con virtual threads**. Ambos exponen `POST /risk` con la misma semántica de request/response. La diferencia está en el runtime, no en el contrato.

### Smoke runner Go

Para correr el smoke runner contra este PoC:

```bash
RISK_SMOKE_BASE_URL=http://localhost:8081 ./bin/risk-smoke --headless
```

## AWS integration

### Ports creados (Phase 1 — bare-javac, sin Gradle)

Se crearon los ports de salida y sus implementaciones de degradación:

| Port | Interface | Adapter activo | Adapter Phase 2 |
|---|---|---|---|
| S3 audit | `domain/repository/AuditEventPublisher` | `NoOpAuditEventPublisher` | `S3AuditEventPublisher` |
| Secrets Manager | `domain/repository/SecretsProvider` | `EnvSecretsProvider` | `MotoSecretsManagerProvider` |

### Por qué opcion (b) y no (a)

AWS SDK v2 tiene ~30 dependencias transitivas (netty, reactive-streams, jackson-cbor, slf4j, HTTP client layers). Bajar jars manualmente con `curl` y mantener el classpath sin un build tool es propenso a errores de versión y convierte el PoC en un ejercicio de gestión de JARs, no de arquitectura. La opción (b) mantiene el PoC funcionando, los ports listos, y deja la tarea de build tool para Phase 2 (Gradle).

### Phase 2 — activar los adapters reales

1. Agregar `build.gradle` con `implementation 'software.amazon.awssdk:s3:2.29.23'`, `secretsmanager:2.29.23`, `url-connection-client:2.29.23`.
2. En `S3AuditEventPublisher.java`: descomentar el bloque del javadoc y eliminar el cuerpo placeholder.
3. En `MotoSecretsManagerProvider.java`: ídem.
4. `RiskApplicationFactory` ya wirea los adapters reales cuando `AWS_ENDPOINT_URL_S3` / `AWS_ENDPOINT_URL_SECRETSMANAGER` están seteados — no requiere cambios.

### Variables de entorno

```bash
# S3 audit (MinIO)
export AWS_ENDPOINT_URL_S3=http://localhost:9000
export RISK_AUDIT_BUCKET=risk-audit
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_REGION=us-east-1

# Secrets Manager (Moto)
export AWS_ENDPOINT_URL_SECRETSMANAGER=http://localhost:5000
```

### Verificar audit log en MinIO (Phase 2)

```bash
# Hacer una decisión
curl -s -X POST http://localhost:8081/risk \
  -H 'Content-Type: application/json' \
  -d '{"transactionId":"tx-1","customerId":"c-1","amountCents":200000}'

# Verificar el objeto en MinIO
aws --endpoint-url http://localhost:9000 s3 ls s3://risk-audit/risk-audit/2026/05/07/
```

## Qué mirar

- `application/usecase/risk/EvaluateTransactionRiskService`: orquesta el caso de uso.
- `domain/service/*Policy`: reglas de negocio puras.
- `domain/repository/*`: contratos hacia dependencias externas (port out).
- `domain/usecase/EvaluateRiskUseCase`: contrato de entrada (port in).
- `infrastructure/repository/*`: implementaciones reemplazables.
- `config/RiskApplicationFactory`: composición manual de dependencias (sin DI framework).
