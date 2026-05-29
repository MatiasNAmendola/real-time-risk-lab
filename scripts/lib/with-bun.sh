#!/usr/bin/env bash
# with-bun.sh — Assert Bun is available, then execute the provided command.
#
# Usage:
#   scripts/lib/with-bun.sh sh -c 'cd sdks/risk-client-typescript && bun install --frozen-lockfile && bun run test'
#
# Bun is the required JS package manager/runtime for this repo. Lifecycle scripts
# are disabled via bunfig.toml (`[install] ignoreScripts = true`) to reduce
# supply-chain/malware risk from prepare/install/postinstall hooks.

set -euo pipefail

if ! command -v bun >/dev/null 2>&1; then
  printf 'with-bun.sh: bun not found on PATH.\n' >&2
  printf '  Install Bun: https://bun.sh/docs/installation\n' >&2
  exit 127
fi

exec "$@"
