#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
bun install --frozen-lockfile
./scripts/check-boundaries.sh
bun run build
bun run test:unit
bun run test:integration
bun run test:e2e
bun run test:atdd:feature
bun run test:smoke

bun run test:k6:optional
