#!/usr/bin/env bash
# scripts/test_nx.sh — Smoke tests for the ./nx CLI
#
# Usage:
#   bash scripts/test_nx.sh
#
# Exit code: 0 if all tests pass, 1 if any fail.

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
NX="$REPO_ROOT/nx"

PASS=0
FAIL=0

_pass() { printf '[PASS] %s\n' "$*"; PASS=$((PASS + 1)); }
_fail() { printf '[FAIL] %s\n' "$*" >&2; FAIL=$((FAIL + 1)); }

assert_exit0() {
  local label="$1"
  shift
  if "$@" >/dev/null 2>&1; then
    _pass "$label"
  else
    _fail "$label (expected exit 0, got $?)"
  fi
}

assert_exit1() {
  local label="$1"
  shift
  if "$@" >/dev/null 2>&1; then
    _fail "$label (expected exit 1, got 0)"
  else
    local code=$?
    if [ "$code" -ne 0 ]; then
      _pass "$label"
    else
      _fail "$label (expected nonzero exit)"
    fi
  fi
}

assert_output_contains() {
  local label="$1"
  local pattern="$2"
  shift 2
  local output
  output="$("$@" 2>&1)" || true
  if echo "$output" | grep -qi "$pattern"; then
    _pass "$label"
  else
    _fail "$label (expected pattern '$pattern' in output)"
    printf '  output was: %s\n' "$output" >&2
  fi
}

# ---------------------------------------------------------------------------
# Precondition: nx must exist and be executable
# ---------------------------------------------------------------------------
if [ ! -f "$NX" ]; then
  printf '[FATAL] ./nx not found at %s\n' "$NX" >&2
  exit 1
fi

if [ ! -x "$NX" ]; then
  chmod +x "$NX"
fi

# ---------------------------------------------------------------------------
# bash -n syntax check
# ---------------------------------------------------------------------------
if bash -n "$NX" 2>/dev/null; then
  _pass "bash -n nx (syntax OK)"
else
  _fail "bash -n nx (syntax errors)"
fi

# ---------------------------------------------------------------------------
# ./nx --help shows help and exits 0
# ---------------------------------------------------------------------------
assert_exit0 "./nx --help exits 0" "$NX" --help
assert_output_contains "./nx --help contains USAGE" "USAGE" "$NX" --help
assert_output_contains "./nx --help contains COMMANDS" "COMMANDS" "$NX" --help
assert_output_contains "./nx --help contains EXAMPLES" "EXAMPLES" "$NX" --help

# ---------------------------------------------------------------------------
# ./nx help (no args) same as --help
# ---------------------------------------------------------------------------
assert_exit0 "./nx help exits 0" "$NX" help
assert_output_contains "./nx help shows commands" "test" "$NX" help

# ---------------------------------------------------------------------------
# ./nx version prints version string
# ---------------------------------------------------------------------------
assert_exit0 "./nx version exits 0" "$NX" version
assert_output_contains "./nx version contains 'nx version'" "nx version" "$NX" version

# ---------------------------------------------------------------------------
# ./nx test --help shows subcommands
# ---------------------------------------------------------------------------
assert_exit0 "./nx test --help exits 0" "$NX" test --help
assert_output_contains "./nx test --help shows 'smoke'" "smoke" "$NX" test --help
assert_output_contains "./nx test --help shows 'atdd'" "atdd" "$NX" test --help
assert_output_contains "./nx test --help shows 'integration'" "integration" "$NX" test --help
assert_output_contains "./nx test all dry-run uses composite" "DRY RUN -- plan" "$NX" test all --dry-run

# ---------------------------------------------------------------------------
# ./nx proc exposes repo-scoped process control
# ---------------------------------------------------------------------------
assert_exit0 "./nx proc status exits 0" env NX_PROC_GUARD_PS_FIXTURE=empty "$NX" proc status
assert_output_contains "./nx proc stop defaults to dry-run" "Dry-run\\|No matching processes" env NX_PROC_GUARD_PS_FIXTURE=empty "$NX" proc stop --only-kind test-runner

# ---------------------------------------------------------------------------
# ./nx bench --help shows subcommands
# ---------------------------------------------------------------------------
assert_exit0 "./nx bench --help exits 0" "$NX" bench --help
assert_output_contains "./nx bench --help shows 'inproc'" "inproc" "$NX" bench --help

# ---------------------------------------------------------------------------
# ./nx demo --help shows subcommands
# ---------------------------------------------------------------------------
assert_exit0 "./nx demo --help exits 0" "$NX" demo --help
assert_output_contains "./nx demo --help shows 'rest'" "rest" "$NX" demo --help

# ---------------------------------------------------------------------------
# ./nx dashboard --help
# ---------------------------------------------------------------------------
assert_exit0 "./nx dashboard --help exits 0" "$NX" dashboard --help

# ---------------------------------------------------------------------------
# ./nx admin --help
# ---------------------------------------------------------------------------
assert_exit0 "./nx admin --help exits 0" "$NX" admin --help

# ---------------------------------------------------------------------------
# Invalid command returns exit 1 + suggestion
# ---------------------------------------------------------------------------
assert_exit1 "./nx unknown-cmd exits 1" "$NX" unknown-cmd-xyz
assert_output_contains "./nx unknown-cmd shows suggestion" "help" "$NX" unknown-cmd-xyz

# ---------------------------------------------------------------------------
# Subcommands with invalid sub also fail properly
# ---------------------------------------------------------------------------
assert_exit1 "./nx test invalid-suite exits 1" "$NX" test invalid-suite-xyz
assert_exit1 "./nx bench invalid exits 1" "$NX" bench invalid-target-xyz
assert_exit1 "./nx demo invalid exits 1" "$NX" demo invalid-demo-xyz
assert_exit1 "./nx run invalid exits 1" "$NX" run invalid-run-target-xyz
assert_exit1 "./nx up invalid exits 1" "$NX" up invalid-up-target-xyz
assert_exit1 "./nx down invalid exits 1" "$NX" down invalid-down-target-xyz

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
TOTAL=$((PASS + FAIL))
printf '\n%d/%d tests passed\n' "$PASS" "$TOTAL"

if [ "$FAIL" -gt 0 ]; then
  printf '%d test(s) FAILED\n' "$FAIL" >&2
  exit 1
fi

exit 0
