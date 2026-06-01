#!/usr/bin/env bash
# .ai/adapters/windsurf/install.sh
# Installs the Windsurf adapter: generates .windsurf/rules/*.md (Wave 8+) and .windsurfrules (legacy compatibility)
# Idempotent.

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
RULES_DIR="$REPO_ROOT/.windsurf/rules"

echo "=== Windsurf adapter install ==="

# --- Wave 8+ format: .windsurf/rules/*.md with trigger frontmatter ---
mkdir -p "$RULES_DIR"

# 00-project.md — always active
cat > "$RULES_DIR/00-project.md" <<'EOF'
---
trigger: always_on
description: Real-Time Risk Lab project context and non-negotiable rules
---

# Project: Real-Time Risk Lab — Architecture Exploration

Technical architecture exploration for transactional risk.
Real-time fraud system: 150 TPS, p99 < 300ms.
Stack: Java 21 LTS executable baseline (Java 25 LTS documented target), Gradle Kotlin DSL, Vert.x 5.0.12, Postgres 16, Valkey 8, Redpanda, k3d/OrbStack.

Full context: .ai/context/architecture.md
PoC inventory: .ai/context/poc-inventory.md

## Non-negotiable rules

1. Java 21 LTS executable baseline; Java 25 LTS remains a documented target.
2. Clean Architecture layout (enterprise Go pattern). See .ai/primitives/rules/architecture-clean.md
3. ATDD first. Write the .feature file BEFORE production code.
4. OTEL on every request: trace + log + metric. correlationId in MDC and response header.
5. domain/ must not import from application/ or infrastructure/.

## Available skills

Before implementing, review: .ai/primitives/skills/
Full rules: .ai/primitives/rules/
Workflows: .ai/primitives/workflows/

## Do not touch

poc/, tests/, cli/, docs/, vault/ — user-owned.
EOF

echo "  created: $RULES_DIR/00-project.md"

# 10-java-arch.md — active for Java files
cat > "$RULES_DIR/10-java-arch.md" <<'EOF'
---
trigger: glob
glob: "**/*.java"
description: Clean Architecture and Java baseline conventions
---

# Architecture rules for Java code

Full rule: .ai/primitives/rules/architecture-clean.md

## Canonical layout

domain/{entity,repository,usecase,service,rule}
application/{usecase/<aggregate>,mapper,dto}
infrastructure/{controller,consumer,repository,resilience,time}
config/ cmd/

## Dependency rule

domain/ <- application/ <- infrastructure/ <- config/cmd/
domain/ must NOT import from application/ or infrastructure/.

## Java baseline

- Java 21 LTS (`--release 21`) in the current build; Java 25 is a documented target
- Virtual threads for blocking I/O
- Records for Value Objects

See: .ai/primitives/rules/naming-conventions.md
EOF

echo "  created: $RULES_DIR/10-java-arch.md"

# 20-testing.md — active for test files
cat > "$RULES_DIR/20-testing.md" <<'EOF'
---
trigger: glob
glob: "**/*.feature"
description: ATDD-first testing strategy
---

# Testing rules

Full rule: .ai/primitives/rules/testing-atdd.md

## ATDD first

1. Write the .feature file BEFORE production code.
2. Run -> FAIL (RED confirmed).
3. Implement the minimum to pass.
4. Run -> PASS (GREEN).

## Frameworks

Karate 1.5+ (PoCs), Cucumber-JVM 7+ (tests/), JUnit 5 (unit).
Coverage: >= 80% line in domain/ and application/.
EOF

echo "  created: $RULES_DIR/20-testing.md"

# --- Legacy compatibility: .windsurfrules for pre-Wave 8 versions ---
cat > "$REPO_ROOT/.windsurfrules" <<'EOF'
# Real-Time Risk Lab — Windsurf Rules (legacy compat, pre-Wave 8)
# For Windsurf Wave 8+, rules live in .windsurf/rules/*.md

## Project

Technical architecture exploration for transactional risk.
Real-time fraud system: 150 TPS, p99 < 300ms.
Stack: Java 21 LTS executable baseline (Java 25 LTS documented target), Gradle Kotlin DSL, Vert.x 5.0.12, Postgres 16, Valkey 8, Redpanda, k3d/OrbStack.

Full context: .ai/context/architecture.md

## Java version

- Java 21 LTS executable baseline; Java 25 LTS remains a documented target.
- --release 21 in the current build.
- Virtual threads for blocking I/O.
- Records for Value Objects.

## Clean Architecture layout

domain/{entity,repository,usecase,service,rule}
application/{usecase/<aggregate>,mapper,dto}
infrastructure/{controller,consumer,repository,resilience,time}
config/ cmd/

Invariant: domain/ must NOT import from application/ or infrastructure/.

## ATDD first

Write the .feature file BEFORE production code.
Frameworks: Karate 1.5+ (PoCs), Cucumber-JVM 7+ (tests/).
Coverage: >= 80% line in domain/ and application/.

## OTEL observability

Every request produces trace + log + metric.
correlationId in MDC, the X-Correlation-Id response header, and Kafka events.
Backend: OpenObserve.

## Do not touch

poc/, tests/, cli/, docs/, vault/ — user-owned.

## Available skills

Before implementing, review: .ai/primitives/skills/
Full rules: .ai/primitives/rules/
Workflows: .ai/primitives/workflows/
EOF

echo "  created: $REPO_ROOT/.windsurfrules (legacy compat)"

echo ""
echo "Windsurf adapter installed."
echo "Wave 8+ rules: $RULES_DIR"
echo "Legacy compat:  $REPO_ROOT/.windsurfrules"
echo "Both files coexist for maximum compatibility."
