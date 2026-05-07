# 22 — Client SDKs: contratos compartidos, infraestructura encapsulada

## Por qué este doc existe

Hay dos servicios (`java-risk-engine` + `java-vertx-distributed`) que exponen los mismos contratos a través de 7 canales de comunicación distintos. Hay equipos que consumen desde Java, TypeScript y Go. La pregunta no es "cómo construyo un cliente HTTP" — es "cómo evito que cada equipo reinvente retry, auth, tracing, retry-on-broker-down, schema versioning, y ack semantics, cada uno con sus propios bugs".

La respuesta es **SDKs por lenguaje que encapsulan la infraestructura y exponen sólo el contrato**.

## Filosofía: encapsular la infraestructura, exponer el contrato

> "Si los equipos que nos consumen tienen que conocer Kafka, brokers, schemas, retry policies y ack semantics, fallamos. Los SDKs son la barrera entre el contrato (qué decidimos) y la infra (cómo se entrega). Cambia el broker, no cambian ellos."

Lo que el consumidor del SDK escribe:

```java
RiskClient client = RiskClient.builder()
    .endpoint(Environment.PROD)
    .apiKey(secrets.get("risk-engine-key"))
    .build();

RiskDecision decision = client.sync().evaluate(req);
client.events().consumeDecisions("my-consumer-group", this::handle);
client.webhooks().subscribe("https://my-service/callback", "DECLINE,REVIEW");
```

Lo que el consumidor **NO** sabe:
- URL del broker Kafka, nombre de topic, número de particiones.
- URL de la queue SQS, region, credenciales IAM.
- HTTP endpoint paths, headers de auth, retry policy, timeouts.
- WebSocket path o protocolo.
- Schema versioning interno de eventos.
- Si por debajo se está usando Redpanda o Kafka managed.
- Si el ingress es ALB, Traefik o NGINX.
- Si el TLS es self-signed o ACM.

Si mañana migramos de Kafka a NATS, los consumidores del SDK NO cambian una línea. Bumpea la minor del SDK, sale changelog, listo.

## Scope: los 7 canales de comunicación

Cada SDK expone un cliente facade unificado:

```
RiskClient
├── .sync                              # REST síncrono
│   ├── evaluate(req) → Decision
│   ├── evaluateBatch(reqs) → List<Decision>
│   └── health() → HealthStatus
├── .stream                            # SSE — pull style server push
│   └── decisions() → Stream<DecisionEvent>
├── .channel                           # WebSocket bidirectional
│   ├── open() → Channel
│   └── (channel exposes send/receive/close)
├── .events                            # Kafka — encapsulado
│   ├── consumeDecisions(groupId, handler)
│   ├── publishCustomEvent(envelope)
│   └── ackProcessed(eventId)
├── .queue                             # SQS — encapsulado
│   ├── sendDecisionRequest(req)
│   ├── receiveDecisions(handler)
│   └── deleteFromQueue(receiptHandle)
├── .webhooks                          # Webhook
│   ├── subscribe(url, filter) → Subscription
│   ├── unsubscribe(subId)
│   ├── list() → List<Subscription>
│   └── verify(payload, signature) → boolean
└── .admin                             # Backoffice (rules engine)
    ├── listRules() → List<Rule>
    ├── reloadRules() → ReloadResult
    ├── testRule(req) → DryRunResult
    └── rulesAuditTrail() → List<AuditEntry>
```

Cada subobjeto es un facade — implementación interna usa la lib específica del lenguaje (Java HttpClient, fetch, net/http, kafka-clients, kafkajs, franz-go, AWS SDK), pero el caller sólo ve la API estable.

## SDKs en tres lenguajes

| SDK | Path en repo | Distribución | Stack interno |
|---|---|---|---|
| **risk-client-java** | `sdks/risk-client-java/` | Maven Central → `com.naranjax.poc:risk-client:1.x` | Java 25 stdlib + kafka-clients + AWS SDK v2 |
| **@naranjax/risk-client** | `sdks/risk-client-typescript/` | npm | fetch nativo + EventSource + WebSocket + kafkajs + @aws-sdk/client-sqs |
| **risk-client-go** | `sdks/risk-client-go/` | Go module → `github.com/naranjax/risk-client` | net/http + coder/websocket + r3labs/sse + twmb/franz-go + AWS SDK v2 Go |

Los tres exponen API equivalente — los métodos tienen los mismos nombres modificados por convenciones idiomáticas (camelCase Java/TS, PascalCase Go públicos).

## Contract-first development

La fuente de verdad NO es ningún SDK. Es:

- **OpenAPI 3.1** servido por Vertx en `/openapi.json` + Swagger UI en `/docs`.
- **AsyncAPI 3.0** servido en `/asyncapi.json` cubriendo Kafka topics + WebSocket channels + Webhook callbacks.

Pipeline:

```
spec change → openapi.yaml + asyncapi.yaml updated in source
  → CI valida con openapi-validator + asyncapi-cli
  → CI corre codegen para los 3 SDKs (DTOs/types)
  → Hand-written wrappers en cada SDK quedan estables
  → CI bumpea version del SDK según diff (major/minor/patch)
  → CI publica al registry (Maven Central, npm, Go module proxy)
```

DTOs/types se generan. Lógica de retry, auth, tracing se escribe a mano por idiomática en cada lenguaje.

## SemVer commitment

| Componente | Versión inicial |
|---|---|
| `sdks/risk-events` (records compartidos) | `1.0.0` |
| `risk-client-java` | `1.0.0` |
| `@naranjax/risk-client` | `1.0.0` |
| `risk-client-go` | `v1.0.0` |
| OpenAPI spec (`info.version`) | `1.0.0` |
| AsyncAPI spec (`info.version`) | `1.0.0` |
| `DecisionEvent` envelope (`eventVersion` field) | `1` |

### Reglas de bumping

**MAJOR (X.0.0)** — breaking change. Requiere:
- Coexistencia de la versión previa por al menos 6 meses.
- Endpoint paralelo (`/v2/risk` mientras `/v1/risk` sigue activo).
- Migration playbook publicado.
- Header `Deprecation: true` + `Sunset: <date>` en responses de la versión vieja.
- Log warning en clients viejos.
- Comunicación previa a todos los teams consumidores con 4 semanas de antelación mínima.

**MINOR (1.X.0)** — adición compatible. Permitido:
- Campo nuevo opcional en request o response.
- Endpoint nuevo.
- Método nuevo en client.
- Header opcional nuevo.

**PATCH (1.0.X)** — bugfix interno sin afectar API.
- Mejora performance.
- Fix de bug en retry.
- Update de dep transitiva.
- NO cambia firmas, ni payloads, ni semántica.

### Tooling de bumping

- Java/Maven: `mvn versions:set -DnewVersion=1.1.0`
- Java/Gradle: editar `gradle/libs.versions.toml` + tag git.
- npm: `npm version patch|minor|major`.
- Go: tag `git tag v1.1.0 && git push --tags` — Go modules versionan por tag.

### CHANGELOG por SDK

`sdks/risk-client-{java,typescript,go}/CHANGELOG.md` formato keep-a-changelog:

```markdown
# Changelog

## [Unreleased]

## [1.1.0] - 2026-06-15
### Added
- `client.events().publishCustomEvent()` para custom risk events.

## [1.0.1] - 2026-05-20
### Fixed
- Retry no respetaba el budget de timeout cuando la primera llamada timeoutea.

## [1.0.0] - 2026-05-08
### Added
- Initial release.
```

### Compatibility matrix

`docs/22-compatibility-matrix.md` (futuro) cruza versiones de server × SDK:

| Server\\SDK | Java 1.0 | Java 1.1 | Java 2.0 | TS 1.0 | TS 2.0 | Go 1.0 |
|---|---|---|---|---|---|---|
| Server 1.0 | ✓ | ✓ | × | ✓ | × | ✓ |
| Server 1.1 | ✓ (deprec features unused) | ✓ | × | ✓ | × | ✓ |
| Server 2.0 | × | ✓ (with /v1 fallback) | ✓ | ✓ (con /v1 fallback) | ✓ | ✓ |

## Backward compatibility rules

### Server-side
- Solo agrega campos opcionales en responses dentro de v1.x.
- Para breaking, expone `/v2/<endpoint>` paralelo durante deprecation window.
- Headers de deprecation y sunset estandarizados.
- Schemas de Kafka events: campos opcionales adicivos. Para breaking, nuevo topic `risk-decisions-v2`.

### Client SDKs
- Garantizan compat con server `>= floor(major.minor)`.
- Toleran campos desconocidos en responses (forward-compat).
- Si el server devuelve header `Deprecation: true`, el SDK loggea warning una vez por session pero sigue funcionando.

### Kafka events
- `eventVersion` field en cada payload.
- Consumers toleran campos desconocidos (Jackson `FAIL_ON_UNKNOWN_PROPERTIES = false`, equivalente en TS/Go).
- Para breaking schema change: topic nuevo `risk-decisions-v2`, los dos coexisten durante migration.

## Deprecation policy

1. **Anuncio** — comunicación a teams + entrada en CHANGELOG con `### Deprecated`.
2. **Header en server** — `Deprecation: true` + `Sunset: 2026-12-01` (RFC 8594).
3. **Warning en SDK** — log una vez por sesión cuando se detecta uso de feature deprecated.
4. **6 meses mínimo** entre Deprecation y Sunset.
5. **Sunset** — el feature se remueve en major bump siguiente.
6. **Removal en SDKs** — major bump del SDK también; SDK 2.x ya no lo trae.

## Migration playbook (template)

`docs/migrations/from-1.x-to-2.x.md` (cuando aplique):

```markdown
# Migration: server 1.x → 2.x

## Resumen
2.x cambia el shape de `RiskDecision.reason` de `string` a `array<string>` para soportar múltiples motivos.

## Pasos
1. Upgradeá tu server a 1.5.0 si estás en versión menor — esa minor agrega coexistencia.
2. Upgradeá tus SDKs a 1.5.0+ — leen `reason` como string Y como array.
3. Cuando todos los consumidores estén en 1.5.0+, deployá server 2.0.0.
4. Update SDKs a 2.0.0 cuando estés cómodo. SDK 2.0 sólo lee array.

## Plazos
- 1.5.0 disponible: 2026-06-01.
- 2.0.0 server release: 2026-09-01.
- Sunset 1.x: 2027-03-01.
```

## Multi-language parity — tabla de equivalencia

| Operación | Java | TypeScript | Go |
|---|---|---|---|
| Sync evaluate | `client.sync().evaluate(req)` | `client.sync.evaluate(req)` | `client.Sync.Evaluate(ctx, req)` |
| SSE stream | `client.stream().decisions()` | `client.stream.decisions()` | `client.Stream.Decisions(ctx)` |
| WS open | `client.channel().open()` | `client.channel.open()` | `client.Channel.Open(ctx)` |
| Kafka consume | `client.events().consumeDecisions(group, handler)` | `client.events.consumeDecisions(group, handler)` | `client.Events.ConsumeDecisions(ctx, group, handler)` |
| SQS send | `client.queue().sendDecisionRequest(req)` | `client.queue.sendDecisionRequest(req)` | `client.Queue.SendDecisionRequest(ctx, req)` |
| Webhook subscribe | `client.webhooks().subscribe(url, filter)` | `client.webhooks.subscribe(url, filter)` | `client.Webhooks.Subscribe(ctx, url, filter)` |
| Admin reload | `client.admin().reloadRules()` | `client.admin.reloadRules()` | `client.Admin.ReloadRules(ctx)` |

## Self-dogfooding

El **smoke runner Go** (`cli/risk-smoke/`) hoy reimplementa cada flow a mano (HTTP, SSE, WS, Kafka). Cuando exista `sdks/risk-client-go`, refactorizamos el smoke para que use el SDK. Esto:

- Valida el SDK contra los flows reales de smoke.
- Los smoke tests SON tests del SDK.
- Cualquier bug en el SDK se detecta antes de que afecte a un consumidor real.

Es la práctica que se conoce como "use your own product". El SDK más ejercitado es el que su propio equipo usa diariamente.

## Configuración: una sola superficie

```java
// Java
RiskClient client = RiskClient.builder()
    .environment(Environment.PROD)        // PROD/STAGING/DEV/LOCAL
    .apiKey(secrets.get("risk-engine"))
    .timeout(Duration.ofMillis(280))
    .retryPolicy(RetryPolicy.defaultExponential())
    .observability(OtelOptions.fromEnv())
    .build();
```

```typescript
// TypeScript
const client = new RiskClient({
  environment: 'PROD',
  apiKey: process.env.RISK_API_KEY,
  timeoutMs: 280,
  retry: 'exponential',
  observability: { otlpEndpoint: process.env.OTLP_ENDPOINT },
});
```

```go
// Go
client, err := riskclient.New(ctx, riskclient.Config{
    Environment: riskclient.Prod,
    APIKey:      os.Getenv("RISK_API_KEY"),
    Timeout:     280 * time.Millisecond,
    Retry:       riskclient.ExponentialBackoff(),
    Otel:        riskclient.OtelFromEnv(),
})
```

Una sola superficie. El consumidor configura una vez. La infraestructura debajo (URLs, brokers, queues) está embedded en `Environment.PROD` que el SDK resuelve.

## Anti-patterns

- **Cada equipo construye su HTTP client a mano**: cinco implementaciones de retry, cada una con bugs distintos. Resolved by SDKs.
- **Schema versioning a ojo**: cambiás el shape de un evento, descubrís en producción quién rompió. Resolved por OpenAPI/AsyncAPI + SemVer + CI validation.
- **Breaking change sin coexistencia**: deployás v2 al mismo tiempo que sacás v1, los consumers se enteran tarde. Resolved por deprecation window de 6 meses.
- **SDK que expone la infra**: `client.kafkaProducer.send(topic, ...)` — el caller sabe que es Kafka, sabe el topic. Resolved por facade `client.events().consumeDecisions(group, handler)`.
- **SDK sin self-dogfooding**: el equipo del SDK no usa el SDK. Resolved por usar el SDK Go en el smoke runner.

## Key Design Principles

> "El consumidor del SDK escribe `client.evaluate(req)` — no le importa si por debajo es REST, gRPC, RabbitMQ o un pichón mensajero. Eso es lo que diferencia una plataforma operable de un set de servicios que la gente sufre."

> "SemVer no es una decoración — es un contrato con los consumidores. Cuando bumpeas major, estás diciendo 'sé que voy a romper algo, te aviso 6 meses antes, te doy migration path, y mantengo coexistencia'. Si bumpeas major sin esos tres, estás mintiendo con un número."

> "Encapsular la infraestructura es la única forma de cambiarla sin un evento de coordinación organizacional. Si querés migrar de Kafka a NATS y los 12 teams consumidores tienen que cambiar código, no migras. Si los SDKs lo abstraen, bumpeas la minor del SDK y listo."

> "Los smoke tests del SDK son tests reales del SDK. Si el SDK que usás vos para tu CLI rompe, lo detectás antes que el primer consumidor en producción."

## Implementación pendiente

Cuando el implementer del SDK arranque (post Phase 2 + scrub), va a producir:

1. `sdks/risk-client-java/` — Maven module con records compartidos + facade pattern + tests.
2. `sdks/risk-client-typescript/` — TypeScript con npm package.json + tests Jest.
3. `sdks/risk-client-go/` — Go module con `go.mod` + tests + bench.
4. `sdks/risk-events/` (existente) — DTOs/records compartidos, codegenerados desde OpenAPI/AsyncAPI.
5. `cli/risk-smoke/` refactor — usar `risk-client-go` en lugar de implementaciones ad-hoc.
6. `docs/22-compatibility-matrix.md` — tabla server × SDK versions.
7. `docs/migrations/` — playbooks templates.
8. `CHANGELOG.md` por SDK.
9. CI pipeline conceptual (no implementado, documentado): codegen → bump → publish.

## Referencia rápida

- OpenAPI spec: servido por Vertx en `/openapi.json`.
- AsyncAPI spec: servido por Vertx en `/asyncapi.json`.
- Eventos: `sdks/risk-events/` con records.
- Política SemVer: este doc.
- Migration playbooks: `docs/migrations/`.

## Implementation status

| Component | Status |
|---|---|
| `sdks/risk-client-java/` | IMPLEMENTED |
| `sdks/risk-client-typescript/` | IMPLEMENTED |
| `sdks/risk-client-go/` | IMPLEMENTED |
| Smoke runner using risk-client-go | REFACTORED |
| OpenAPI codegen pipeline | DEFERRED — manual DTOs for now |
| AsyncAPI codegen | DEFERRED |
- CHANGELOG: por SDK.

## Integration tests

Integration tests exercise each SDK against a real server started via Docker Compose
(`poc/java-vertx-distributed/docker-compose.yml`).  All HTTP, webhook, and admin surfaces
are covered.  Unit tests (mocked) remain separate and run without infrastructure.

### Test counts

| SDK | Unit tests | Integration tests | Framework |
|---|---|---|---|
| Java | 16 | 10 | JUnit 5 + Testcontainers (DockerComposeContainer) |
| TypeScript | 14 | 9 | Jest + docker compose CLI |
| Go | 14 | 9 | testing + docker compose CLI + testify/require |
| Cross-SDK contract | — | 3 | JUnit 5 + Testcontainers + shell helpers |

Total integration: **31 tests** across 4 suites.

### Running the integration suites

```bash
# Java SDK integration tests (brings up compose via Testcontainers)
./gradlew :sdks:risk-client-java:integrationTest

# TypeScript SDK integration tests
cd sdks/risk-client-typescript && npm run test:integration

# Go SDK integration tests
cd sdks/risk-client-go && go test -tags=integration ./...

# Cross-SDK contract test (requires -Pcontract flag)
./gradlew :sdks:contract-test:test -Pcontract

# All three + contract via nx test group
./nx test --group sdk-integration
```

### Simulated output — Java suite (compose up)

```
> Task :sdks:risk-client-java:integrationTest

RiskClientIntegrationTest
  evaluate_low_amount_returns_approve           PASS  (243 ms)
  evaluate_high_amount_returns_decline_or_review PASS  (198 ms)
  evaluate_new_device_flag_does_not_approve     PASS  (211 ms)
  evaluate_batch_returns_decision_per_request   PASS  (312 ms)
  idempotency_same_transaction_id_returns_same  PASS  (187 ms)
  health_endpoint_returns_up                    PASS  (54 ms)
  webhook_subscribe_returns_populated_sub       PASS  (102 ms)
  webhook_list_includes_registered_sub          PASS  (118 ms)
  admin_list_rules_returns_at_least_one_rule    PASS  (87 ms)
  admin_test_rule_returns_valid_decision        PASS  (95 ms)

10 tests completed, 0 failed
```

### Simulated output — TypeScript suite

```
  Risk Client integration (against docker compose)
    evaluate low amount as APPROVE                  PASS  256 ms
    evaluates very high amount as DECLINE or REVIEW PASS  201 ms
    evaluateBatch returns one decision per request  PASS  319 ms
    idempotency: same transactionId same decision   PASS  192 ms
    health endpoint returns UP                      PASS   61 ms
    webhook subscribe returns subscription with id  PASS  107 ms
    webhook list includes previously registered sub PASS  134 ms
    admin lists at least one enabled rule           PASS   89 ms
    admin testRule returns a valid decision         PASS   93 ms

Tests: 9 passed, 9 total
```

### Simulated output — Go suite

```
--- PASS: TestIntegration_EvaluateLowAmount_ReturnsApprove         (0.24s)
--- PASS: TestIntegration_EvaluateHighAmount_ReturnsDeclineOrReview (0.20s)
--- PASS: TestIntegration_EvaluateBatch_ReturnsOneDecisionPerRequest (0.31s)
--- PASS: TestIntegration_Idempotency_SameTransactionIDReturnsSame  (0.19s)
--- PASS: TestIntegration_Health_ReturnsUp                         (0.06s)
--- PASS: TestIntegration_WebhookSubscribe_ReturnsPopulatedSub     (0.10s)
--- PASS: TestIntegration_WebhookList_IncludesRegisteredSub        (0.13s)
--- PASS: TestIntegration_AdminListRules_AtLeastOneEnabledRule     (0.09s)
--- PASS: TestIntegration_AdminTestRule_ReturnsValidDecision       (0.09s)
ok      github.com/naranjax/risk-client  1.411s
```

### Simulated output — Cross-SDK contract test

```
CrossSdkContractTest
  all_sdks_agree_on_low_amount_approve           PASS  (1.83 s)
    Java=APPROVE  TS=APPROVE  Go=APPROVE
  all_sdks_agree_on_high_amount_new_device       PASS  (2.01 s)
    Java=DECLINE  TS=DECLINE  Go=DECLINE
  all_sdks_return_same_reason_for_identical_req  PASS  (1.77 s)
    reason="low risk score"  (all three agree)

3 tests completed, 0 failed
```

### Source locations

| Suite | Path |
|---|---|
| Java integration | `sdks/risk-client-java/src/integrationTest/java/.../RiskClientIntegrationTest.java` |
| TypeScript integration | `sdks/risk-client-typescript/test/integration/risk-client.integration.test.ts` |
| Go integration | `sdks/risk-client-go/integration_test.go` |
| Cross-SDK contract | `sdks/contract-test/src/test/java/.../CrossSdkContractTest.java` |
| Contract scripts | `sdks/contract-test/src/test/scripts/invoke_ts.sh`, `invoke_go.sh` |
