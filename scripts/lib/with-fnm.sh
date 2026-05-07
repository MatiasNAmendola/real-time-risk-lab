#!/usr/bin/env bash
# with-fnm.sh — Activate fnm Node env then exec the rest of the args.
#
# Usage:
#   scripts/lib/with-fnm.sh <command> [args...]
#   scripts/lib/with-fnm.sh sh -c 'cd sdks/risk-client-typescript && npm install --silent && npm test'
#
# Why: the user manages Node.js via fnm (https://github.com/Schniz/fnm). Without
# `eval "$(fnm env)"`, neither `node` nor `npm` are on PATH even when fnm is
# installed via Homebrew. The test runner spawns subshells that do not inherit
# the interactive `--use-on-cd` activation, so we activate explicitly here.
#
# Fallback: if fnm is NOT installed but node/npm are already on PATH (e.g. via
# nvm, asdf, system package, Volta), exec the command anyway. This keeps the
# helper backward-compatible with non-fnm environments.

set -euo pipefail

if [[ $# -eq 0 ]]; then
  printf 'with-fnm.sh: missing command to exec\n' >&2
  exit 2
fi

if command -v fnm >/dev/null 2>&1; then
  # `fnm env` prints shell exports for FNM_DIR, PATH, etc. Eval them so node/npm
  # become available in this process before exec.
  eval "$(fnm env --shell bash 2>/dev/null || fnm env 2>/dev/null)"
elif command -v node >/dev/null 2>&1 && command -v npm >/dev/null 2>&1; then
  : # node/npm already on PATH via another manager; nothing to do.
else
  printf 'with-fnm.sh: neither fnm nor node+npm found on PATH.\n' >&2
  printf '  Install fnm: brew install fnm && fnm install --lts\n' >&2
  printf '  Or install Node.js >=20 via your preferred manager.\n' >&2
  exit 127
fi

exec "$@"
