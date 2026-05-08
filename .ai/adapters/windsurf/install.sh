#!/usr/bin/env bash
# .ai/adapters/windsurf/install.sh
# Instala el adapter de Windsurf: genera .windsurf/rules/*.md (Wave 8+) Y .windsurfrules (compat legacy)
# Idempotente.

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
RULES_DIR="$REPO_ROOT/.windsurf/rules"

echo "=== Windsurf adapter install ==="

# --- Wave 8+ format: .windsurf/rules/*.md con frontmatter de trigger ---
mkdir -p "$RULES_DIR"

# 00-project.md — siempre activo
cat > "$RULES_DIR/00-project.md" <<'EOF'
---
trigger: always_on
description: Real-Time Risk Lab project context and non-negotiable rules
---

# Proyecto: Real-Time Risk Lab — Architecture Exploration

Exploración técnica de arquitectura de riesgo transaccional.
Sistema de fraude tiempo real: 150 TPS, p99 < 300ms.
Stack: Java 21 LTS baseline operativo (Java 25 LTS objetivo documentado), Gradle Kotlin DSL, Vert.x 5.0.12, Postgres 16, Valkey 8, Redpanda, k3d/OrbStack.

Full context: .ai/context/architecture.md
PoC inventory: .ai/context/poc-inventory.md

## Reglas non-negotiable

1. Java 21 LTS baseline operativo; Java 25 LTS queda como objetivo documentado.
2. Clean Architecture layout (enterprise Go pattern). Ver .ai/primitives/rules/architecture-clean.md
3. ATDD primero. Escribir .feature ANTES del codigo de produccion.
4. OTEL en todo request: trace + log + metric. correlationId en MDC y header.
5. domain/ no importa de application/ ni infrastructure/.

## Skills disponibles

Antes de implementar, revisar: .ai/primitives/skills/
Rules completas: .ai/primitives/rules/
Workflows: .ai/primitives/workflows/

## No tocar

poc/, tests/, cli/, docs/, vault/ — ownership del usuario.
EOF

echo "  created: $RULES_DIR/00-project.md"

# 10-java-arch.md — activo en archivos Java
cat > "$RULES_DIR/10-java-arch.md" <<'EOF'
---
trigger: glob
glob: "**/*.java"
description: Clean Architecture and Java baseline conventions
---

# Architecture rules for Java code

Full rule: .ai/primitives/rules/architecture-clean.md

## Layout canonico

domain/{entity,repository,usecase,service,rule}
application/{usecase/<aggregate>,mapper,dto}
infrastructure/{controller,consumer,repository,resilience,time}
config/ cmd/

## Dependency rule

domain/ <- application/ <- infrastructure/ <- config/cmd/
domain/ must NOT import from application/ or infrastructure/.

## Java baseline

- Java 21 LTS (`--release 21`) en el build actual; Java 25 es objetivo documentado
- Virtual threads para I/O bloqueante
- Records para Value Objects

See: .ai/primitives/rules/naming-conventions.md
EOF

echo "  created: $RULES_DIR/10-java-arch.md"

# 20-testing.md — activo en archivos de test
cat > "$RULES_DIR/20-testing.md" <<'EOF'
---
trigger: glob
glob: "**/*.feature"
description: ATDD-first testing strategy
---

# Testing rules

Full rule: .ai/primitives/rules/testing-atdd.md

## ATDD first

1. Escribir .feature ANTES del codigo de produccion.
2. Run -> FAIL (RED confirmed).
3. Implementar minimo para pasar.
4. Run -> PASS (GREEN).

## Frameworks

Karate 1.5+ (PoCs), Cucumber-JVM 7+ (tests/), JUnit 5 (unit).
Coverage: >= 80% line en domain/ y application/.
EOF

echo "  created: $RULES_DIR/20-testing.md"

# --- Legacy compat: .windsurfrules para versiones pre-Wave 8 ---
cat > "$REPO_ROOT/.windsurfrules" <<'EOF'
# Real-Time Risk Lab — Windsurf Rules (legacy compat, pre-Wave 8)
# Para Windsurf Wave 8+, las rules estan en .windsurf/rules/*.md

## Proyecto

Exploración técnica de arquitectura de riesgo transaccional.
Sistema de fraude tiempo real: 150 TPS, p99 < 300ms.
Stack: Java 21 LTS baseline operativo (Java 25 LTS objetivo documentado), Gradle Kotlin DSL, Vert.x 5.0.12, Postgres 16, Valkey 8, Redpanda, k3d/OrbStack.

Full context: .ai/context/architecture.md

## Java version

- Java 21 LTS baseline operativo; Java 25 LTS queda como objetivo documentado.
- --release 21 en el build actual.
- Virtual threads para I/O bloqueante.
- Records para Value Objects.

## Clean Architecture layout

domain/{entity,repository,usecase,service,rule}
application/{usecase/<aggregate>,mapper,dto}
infrastructure/{controller,consumer,repository,resilience,time}
config/ cmd/

Invariante: domain/ NO importa de application/ ni infrastructure/.

## ATDD first

Escribir .feature ANTES del codigo de produccion.
Frameworks: Karate 1.5+ (PoCs), Cucumber-JVM 7+ (tests/).
Coverage: >= 80% line en domain/ y application/.

## Observabilidad OTEL

Todo request produce trace + log + metric.
correlationId en MDC, en response header X-Correlation-Id, en eventos Kafka.
Backend: OpenObserve.

## No tocar

poc/, tests/, cli/, docs/, vault/ — ownership del usuario.

## Skills disponibles

Antes de implementar, revisar: .ai/primitives/skills/
Rules completas: .ai/primitives/rules/
Workflows: .ai/primitives/workflows/
EOF

echo "  created: $REPO_ROOT/.windsurfrules (legacy compat)"

echo ""
echo "Windsurf adapter installed."
echo "Wave 8+ rules: $RULES_DIR"
echo "Legacy compat:  $REPO_ROOT/.windsurfrules"
echo "Both files coexist for maximum compatibility."
