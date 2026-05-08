#!/usr/bin/env bash
# Tests for secret-detector.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HOOK="$SCRIPT_DIR/secret-detector.sh"

PASS=0
FAIL=0

# Helper to run a test case
run_test() {
  local name="$1"
  local tool="$2"
  local payload="$3"
  local expect_exit="${4:-0}"
  local expect_redacted="${5:-}"   # substring that must appear in output (or empty to skip)
  local expect_original="${6:-}"   # substring that must NOT appear in output (or empty to skip)

  local actual_output actual_exit
  # Export env var before the subshell so it propagates through the pipe
  actual_output="$(export CLAUDE_TOOL_NAME="$tool"; printf '%s' "$payload" | bash "$HOOK" 2>/dev/null)" || actual_exit=$?
  actual_exit="${actual_exit:-0}"

  local failed=false

  if [ "$actual_exit" -ne "$expect_exit" ]; then
    printf 'FAIL [%s]: expected exit %s, got %s\n' "$name" "$expect_exit" "$actual_exit"
    failed=true
  fi

  if [ -n "$expect_redacted" ] && ! printf '%s' "$actual_output" | grep -qF "$expect_redacted" 2>/dev/null; then
    printf 'FAIL [%s]: expected "%s" in output\n  output: %s\n' "$name" "$expect_redacted" "$actual_output"
    failed=true
  fi

  if [ -n "$expect_original" ] && printf '%s' "$actual_output" | grep -qF "$expect_original" 2>/dev/null; then
    printf 'FAIL [%s]: original secret "%s" should not appear in output\n' "$name" "$expect_original"
    failed=true
  fi

  if $failed; then
    FAIL=$((FAIL + 1))
  else
    printf 'PASS [%s]\n' "$name"
    PASS=$((PASS + 1))
  fi
}

# Test 1: AWS AKIA token detected and redacted
AWS_SAMPLE_KEY="AKIA""IOSFODNN7EXAMPLE"
PAYLOAD_AWS="{\"command\":\"echo $AWS_SAMPLE_KEY doing something\"}"
run_test "aws_akia_redacted" "Bash" "$PAYLOAD_AWS" 0 "[REDACTED]" "$AWS_SAMPLE_KEY"

# Test 2: JWT detected and redacted
JWT_HEADER="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
JWT_PAYLOAD="eyJzdWIiOiIxMjM0NTY3ODkwIn0"
JWT_SIGNATURE="SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"
JWT="$JWT_HEADER.$JWT_PAYLOAD.$JWT_SIGNATURE"
PAYLOAD_JWT="{\"content\":\"Authorization: Bearer $JWT\"}"
run_test "jwt_redacted" "Write" "$PAYLOAD_JWT" 0 "[REDACTED]" "$JWT_HEADER"

# Test 3: Connection string redacted
DB_SAMPLE_URL="post""gres://""admin"":""s3cr3tP4ss""@""db.host.com:5432/mydb"
PAYLOAD_CONN="{\"new_string\":\"db_url = $DB_SAMPLE_URL\"}"
run_test "conn_string_redacted" "Edit" "$PAYLOAD_CONN" 0 "[REDACTED]" "s3cr3tP4ss"

# Test 4: Benign string passes without change
PAYLOAD_BENIGN='{"command":"echo hello world && ls -la"}'
BENIGN_OUT="$(export CLAUDE_TOOL_NAME="Bash"; printf '%s' "$PAYLOAD_BENIGN" | bash "$HOOK" 2>/dev/null)"
if printf '%s' "$BENIGN_OUT" | grep -qF "echo hello world" 2>/dev/null; then
  printf 'PASS [benign_string_pass]\n'
  PASS=$((PASS + 1))
else
  printf 'FAIL [benign_string_pass]: benign content should pass through unchanged\n  output: %s\n' "$BENIGN_OUT"
  FAIL=$((FAIL + 1))
fi

# Test 5: Entire input is secret -> exit 2 (deny)
PURE_SECRET="{\"command\":\"$AWS_SAMPLE_KEY\"}"
run_test "all_secret_denied" "Bash" "$PURE_SECRET" 2 "deny" ""

# Test 6: Multi-line string with multiple secrets — all redacted
DB_MULTI_URL="post""gres://""user"":""hunter2""@""db.example.com/prod"
MULTILINE_CONTENT="line1: normal text
aws_key=$AWS_SAMPLE_KEY
db=$DB_MULTI_URL
other line"
PAYLOAD_MULTI="{\"content\":$(printf '%s' "$MULTILINE_CONTENT" | jq -Rs .)}"
run_test "multiline_all_redacted" "Write" "$PAYLOAD_MULTI" 0 "[REDACTED]" "hunter2"

# Summary
printf '\n%s passed, %s failed\n' "$PASS" "$FAIL"
if [ "$FAIL" -gt 0 ]; then
  exit 1
fi
exit 0
