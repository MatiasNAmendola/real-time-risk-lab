#!/usr/bin/env bash
# .ai/adapters/cursor/install.sh
# Instala el adapter de Cursor: crea .cursor/rules/*.mdc
# Idempotente.

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
RULES_DIR="$REPO_ROOT/.cursor/rules"

echo "=== Cursor adapter install ==="
mkdir -p "$RULES_DIR"

# 00-project.mdc — siempre activo
cat > "$RULES_DIR/00-project.mdc" <<'EOF'
---
description: Real-Time Risk Lab project context and non-negotiable rules
globs: []
alwaysApply: true
---

# Proyecto: Real-Time Risk Lab — Architecture Exploration

Este repo es una exploración técnica de arquitectura de riesgo transaccional.

## Contexto clave

- Sistema de fraude tiempo real: 150 TPS, p99 < 300ms
- Stack: Java 21 LTS baseline operativo (Java 25 LTS objetivo documentado), Gradle Kotlin DSL, Vert.x 5.0.12, Postgres 16, Valkey 8, Redpanda, k3d/OrbStack
- PoC portfolio en poc/: no-vertx-clean-engine, Vert.x variants, service-mesh demo, k8s-local
- Full context: @.ai/context/architecture.md

## Reglas non-negotiable

1. Java 21 LTS baseline operativo. Ver @.ai/primitives/rules/java-version.md
2. Layout canonico enterprise Go. Ver @.ai/primitives/rules/architecture-clean.md
3. ATDD primero. Ver @.ai/primitives/rules/testing-atdd.md
4. OTEL en todo request. Ver @.ai/primitives/rules/observability-otel.md
5. Clean boundaries: domain no importa infrastructure. Ver @.ai/primitives/rules/clean-arch-boundaries.md

## Skills disponibles

Antes de implementar cualquier cosa, buscar el skill correspondiente en @.ai/primitives/skills/

## NO tocar

poc/, tests/, cli/, docs/, vault/ — estos directorios son ownership del usuario.
EOF

echo "  created: $RULES_DIR/00-project.mdc"

# 10-architecture.mdc — activo en .java
cat > "$RULES_DIR/10-architecture.mdc" <<'EOF'
---
description: Clean Architecture and Java conventions for Real-Time Risk Lab risk engine
globs: ["**/*.java", "**/*.gradle.kts"]
alwaysApply: false
---

# Architecture rules for Java code

See full rules: @.ai/primitives/rules/architecture-clean.md

## Quick reference

- Domain layer: domain/{entity,repository,usecase,service,rule}
  - NO imports from application/ or infrastructure/
- Application layer: application/{usecase/<aggregate>,mapper,dto}
  - NO imports from infrastructure/
- Infrastructure layer: infrastructure/{controller,consumer,repository,resilience,time}
- Config + cmd: wiring only

## Java baseline

- Java 21 LTS (`--release 21`) en el build actual; Java 25 es objetivo documentado
- Use records for Value Objects
- Use virtual threads for blocking I/O

## Naming

- Classes: PascalCase
- Methods/fields: camelCase
- SQL: snake_case
- Files: PascalCase.java

See: @.ai/primitives/rules/naming-conventions.md
EOF

echo "  created: $RULES_DIR/10-architecture.mdc"

# 20-testing.mdc — activo en tests
cat > "$RULES_DIR/20-testing.mdc" <<'EOF'
---
description: ATDD-first testing strategy for Real-Time Risk Lab risk engine
globs: ["**/src/test/**/*.java", "**/*.feature"]
alwaysApply: false
---

# Testing rules

See full rule: @.ai/primitives/rules/testing-atdd.md

## ATDD first

1. Write the .feature file BEFORE any production code
2. Run → must FAIL (RED confirmed)
3. Implement minimum to pass
4. Run → must PASS (GREEN)

## Frameworks

- Karate 1.5+: poc/vertx-layer-as-pod-eventbus/atdd-tests/
- Cucumber-JVM 7+: tests/risk-engine-atdd/
- JUnit 5: unit tests in each module

## Coverage targets

- domain/ and application/: >= 80% line, >= 75% branch
- Run: ./gradlew :<module>:test :<module>:jacocoTestReport

## Never

- Thread.sleep in async tests (use VertxTestContext or awaitility)
- @Disabled without a comment and issue reference
EOF

echo "  created: $RULES_DIR/20-testing.mdc"

echo ""
echo "Cursor adapter installed."
echo "Rules created in: $RULES_DIR"
echo "Restart Cursor to pick up the new rules."
