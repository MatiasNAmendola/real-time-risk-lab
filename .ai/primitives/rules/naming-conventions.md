---
name: naming-conventions
applies_to: ["**/*.java", "**/*.yaml", "**/*.sql", "**/*.md", "**/build.gradle.kts"]
priority: medium
---

# Regla: naming-conventions

## Java

| Elemento | Convencion | Ejemplo |
|---|---|---|
| Clases | PascalCase | `RiskDecision`, `EvaluateTransactionUseCase` |
| Interfaces | PascalCase (sin prefijo I) | `TransactionRepository`, no `ITransactionRepository` |
| Metodos | camelCase | `evaluateTransaction()`, `findById()` |
| Variables | camelCase | `correlationId`, `amountARS` |
| Constantes | SCREAMING_SNAKE_CASE | `MAX_RETRY_COUNT`, `DEFAULT_TIMEOUT_MS` |
| Packages | lowercase.separado.puntos | `io.riskplatform.engine.domain.entity` |
| Enums | PascalCase, valores SCREAMING_SNAKE | `RiskDecision.APPROVE`, `RiskDecision.DECLINE` |

## Archivos y directorios

| Tipo | Convencion | Ejemplo |
|---|---|---|
| Archivos Java | PascalCase.java | `TransactionMapper.java` |
| Feature files | kebab-case.feature | `risk-evaluation.feature` |
| YAML/properties | kebab-case | `application-local.yaml` |
| Shell scripts | kebab-case.sh | `run.sh`, `up.sh` |
| Documentacion | NN-kebab-case.md | `00-mapa-tecnico.md` |
| Helm charts | kebab-case | `risk-engine/` |

## SQL

| Elemento | Convencion | Ejemplo |
|---|---|---|
| Tablas | snake_case, plural | `risk_decisions`, `outbox_events` |
| Columnas | snake_case | `correlation_id`, `occurred_at` |
| Indices | `idx_<tabla>_<columna>` | `idx_risk_decisions_tx_id` |
| Funciones | snake_case | `get_pending_decisions()` |

## Kubernetes / Helm

- Resources: `kebab-case` en `metadata.name`. Ejemplo: `risk-engine-deployment`.
- Labels: `app.kubernetes.io/name: risk-engine`.
- Namespaces: `risk-engine`, `monitoring`, `argocd`.

## Kafka topics

- kebab-case: `risk-decisions`, `fraud-alerts`, `risk-commands-dlq`.

## No usar

- Abreviaturas no estandar: `mgr`, `proc`, `svc` en nombres de clase Java.
- Nombres que incluyen el tipo: `TransactionList` (usar `List<Transaction>`).
- Nombres en espanol mezclados con ingles: `evaluarTransaction` (elegir un idioma por contexto).
