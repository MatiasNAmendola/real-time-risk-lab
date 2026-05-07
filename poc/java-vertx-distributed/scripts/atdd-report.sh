#!/usr/bin/env bash
# ─── atdd-report.sh ───────────────────────────────────────────────────────────
# Post-processes Karate JSON reports into structured Markdown output.
#
# Inputs (read from target/karate-reports/ after mvn test):
#   - karate-summary.json         (Karate's own summary — if present)
#   - *.json                      (per-feature Cucumber JSON files)
#   - karate.log                  (full debug log, written by logback-test.xml)
#
# Output:
#   out/atdd-karate/<timestamp>/
#     summary.md
#     summary.txt
#     features/<id>-<name>.md
#     full.log                  (copy of karate.log)
#     meta.json
#   out/atdd-karate/latest -> <timestamp>   (symlink)
#
# Usage:
#   ./scripts/atdd-report.sh [exit_code]   (exit_code forwarded from mvn test)
# ──────────────────────────────────────────────────────────────────────────────
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPORTS_DIR="$ROOT/atdd-tests/target/karate-reports"
BASE_URL="${CONTROLLER_URL:-http://localhost:8080}"
EXIT_CODE="${1:-0}"
TIMESTAMP="$(date -u +%Y-%m-%dT%H-%M-%S)"
OUT_DIR="$ROOT/out/atdd-karate/$TIMESTAMP"
FEATURES_DIR="$OUT_DIR/features"
COVERAGE_DIR="$OUT_DIR/coverage"

mkdir -p "$FEATURES_DIR" "$COVERAGE_DIR"

# ── Helpers ───────────────────────────────────────────────────────────────────

log() { echo "[atdd-report] $*"; }

# Sanitise a feature name into a slug suitable for filenames
slugify() {
  echo "$1" | tr '[:upper:]' '[:lower:]' | sed 's/[^a-z0-9]/-/g' | sed 's/--*/-/g' | sed 's/^-//;s/-$//'
}

# ── Collect feature JSON files ────────────────────────────────────────────────

# Karate 1.5 writes one JSON file per feature (Cucumber format) under
# target/karate-reports/features/  or directly in target/karate-reports/.
# We glob both locations.

mapfile -t FEATURE_JSONS < <(
  find "$REPORTS_DIR" -name "*.json" \
    ! -name "karate-summary.json" \
    ! -name "*.config.json" \
    2>/dev/null | sort
)

if [[ ${#FEATURE_JSONS[@]} -eq 0 ]]; then
  log "WARNING: no feature JSON files found under $REPORTS_DIR"
  log "         Generating empty summary only."
fi

# ── Per-feature markdown ──────────────────────────────────────────────────────

declare -a SUMMARY_ROWS   # tab-separated: idx  name  total  pass  fail  skip  file
TOTAL_SCENARIOS=0
TOTAL_PASS=0
TOTAL_FAIL=0
TOTAL_SKIP=0

idx=0
for JSON_FILE in "${FEATURE_JSONS[@]}"; do
  idx=$((idx + 1))
  PADDED="$(printf '%02d' "$idx")"

  # Extract feature name from JSON (first element's .name or .uri)
  FEATURE_NAME="$(jq -r '.[0].name // .[0].uri // "feature-'"$idx"'"' "$JSON_FILE" 2>/dev/null || echo "feature-$idx")"
  SLUG="$(slugify "$FEATURE_NAME")"
  MD_FILE="$FEATURES_DIR/${PADDED}-${SLUG}.md"

  # Count scenarios
  F_TOTAL=$(jq '[.[].elements // [] | .[]] | length' "$JSON_FILE" 2>/dev/null || echo 0)
  F_PASS=$(jq '[.[].elements // [] | .[] | select(.steps | map(.result.status) | all(. == "passed"))] | length' "$JSON_FILE" 2>/dev/null || echo 0)
  F_FAIL=$(jq '[.[].elements // [] | .[] | select(.steps | map(.result.status) | any(. == "failed"))] | length' "$JSON_FILE" 2>/dev/null || echo 0)
  F_SKIP=$((F_TOTAL - F_PASS - F_FAIL))

  # Feature-level duration (sum of all step durations in nanoseconds → ms)
  F_DURATION_NS=$(jq '[.[].elements // [] | .[].steps // [] | .[].result.duration // 0] | add // 0' "$JSON_FILE" 2>/dev/null || echo 0)
  F_DURATION_MS=$(( F_DURATION_NS / 1000000 ))
  if (( F_DURATION_MS >= 1000 )); then
    F_DURATION_STR="$(echo "scale=1; $F_DURATION_MS/1000" | bc)s"
  else
    F_DURATION_STR="${F_DURATION_MS}ms"
  fi

  TOTAL_SCENARIOS=$((TOTAL_SCENARIOS + F_TOTAL))
  TOTAL_PASS=$((TOTAL_PASS + F_PASS))
  TOTAL_FAIL=$((TOTAL_FAIL + F_FAIL))
  TOTAL_SKIP=$((TOTAL_SKIP + F_SKIP))

  SUMMARY_ROWS+=("${PADDED}	${FEATURE_NAME}	${F_TOTAL}	${F_PASS}	${F_FAIL}	${F_SKIP}	${PADDED}-${SLUG}.md	${F_DURATION_STR}")

  # ── Write feature markdown ────────────────────────────────────────────────
  {
    echo "# Feature: $FEATURE_NAME"
    echo ""
    # try to get the file path from .uri
    URI="$(jq -r '.[0].uri // ""' "$JSON_FILE" 2>/dev/null || true)"
    [[ -n "$URI" ]] && echo "**File**: \`$URI\`"
    echo "**Scenarios**: $F_TOTAL · **PASS**: $F_PASS · **FAIL**: $F_FAIL · **SKIP**: $F_SKIP · **Duration**: $F_DURATION_STR"
    echo ""

    # Each scenario
    jq -r '.[].elements // [] | .[] |
      "## Scenario: \(.name)\n\n**Status**: \(if .steps | map(.result.status) | any(. == "failed") then "FAIL" elif .steps | map(.result.status) | any(. == "skipped") then "SKIP" else "PASS" end) · **Duration**: \(([.steps[].result.duration // 0] | add // 0) / 1000000 | floor)ms\n\n### Steps\n\n\([ .steps[] | "1. \(if .result.status == "passed" then "✓" elif .result.status == "failed" then "✗" else "-" end) \(.keyword) \(.name) (\((.result.duration // 0) / 1000000 | floor)ms)\(if .result.error_message then "\n   - **Error**: `\(.result.error_message | split("\n")[0])`" else "" end)\(if .rows then "\n   - Rows: \(.rows | length)" else "" end)" ] | join("\n"))\n"
    ' "$JSON_FILE" 2>/dev/null || echo "_No scenario detail available_"
  } > "$MD_FILE"

done

# ── Copy coverage (aggregated cross-module preferred, fallback to per-module) ──

JACOCO_AGGREGATED="$ROOT/atdd-tests/target/site/jacoco-aggregated"
JACOCO_LOCAL="$ROOT/atdd-tests/target/site/jacoco"
JACOCO_XML=""
COVERAGE_REPORT_LABEL=""

if [[ -d "$JACOCO_AGGREGATED" ]]; then
  cp -r "$JACOCO_AGGREGATED/." "$COVERAGE_DIR/"
  JACOCO_XML="$JACOCO_AGGREGATED/jacoco.xml"
  COVERAGE_REPORT_LABEL="aggregated (cross-module)"
elif [[ -d "$JACOCO_LOCAL" ]]; then
  cp -r "$JACOCO_LOCAL/." "$COVERAGE_DIR/"
  JACOCO_XML="$JACOCO_LOCAL/jacoco.xml"
  COVERAGE_REPORT_LABEL="local (atdd-tests module only)"
fi

# Parse per-package coverage from jacoco.xml using Python inline
declare -A PKG_LINE PKG_BRANCH
TOTAL_LINE_COV="n/a"
TOTAL_BRANCH_COV="n/a"
COVERAGE_TABLE_ROWS=""

if [[ -n "$JACOCO_XML" && -f "$JACOCO_XML" ]]; then
  COVERAGE_DATA="$(python3 - "$JACOCO_XML" <<'PYEOF'
import sys, xml.etree.ElementTree as ET

tree = ET.parse(sys.argv[1])
root = tree.getroot()

rows = []
total_line_covered = total_line_missed = 0
total_branch_covered = total_branch_missed = 0

for pkg in root.findall('package'):
    name = pkg.get('name', '').replace('/', '.')
    line_cov = branch_cov = "n/a"
    for counter in pkg.findall('counter'):
        ctype = counter.get('type')
        covered = int(counter.get('covered', 0))
        missed = int(counter.get('missed', 0))
        total = covered + missed
        pct = round(100 * covered / total) if total > 0 else 0
        if ctype == 'LINE':
            line_cov = f"{pct}%"
            total_line_covered += covered
            total_line_missed += missed
        elif ctype == 'BRANCH':
            branch_cov = f"{pct}%"
            total_branch_covered += covered
            total_branch_missed += missed
    if name.startswith('com.naranjax'):
        rows.append(f"{name}|{line_cov}|{branch_cov}")

total_line = total_line_covered + total_line_missed
total_branch = total_branch_covered + total_branch_missed
total_line_pct = round(100 * total_line_covered / total_line) if total_line > 0 else 0
total_branch_pct = round(100 * total_branch_covered / total_branch) if total_branch > 0 else 0

for r in rows:
    print(r)
print(f"__total__|{total_line_pct}%|{total_branch_pct}%")
PYEOF
  )" || COVERAGE_DATA=""

  if [[ -n "$COVERAGE_DATA" ]]; then
    while IFS='|' read -r pkg lc bc; do
      if [[ "$pkg" == "__total__" ]]; then
        TOTAL_LINE_COV="$lc"
        TOTAL_BRANCH_COV="$bc"
      else
        COVERAGE_TABLE_ROWS+="| $pkg | $lc | $bc |"$'\n'
      fi
    done <<< "$COVERAGE_DATA"
  fi
fi

# ── Copy full log ─────────────────────────────────────────────────────────────

if [[ -f "$REPORTS_DIR/karate.log" ]]; then
  cp "$REPORTS_DIR/karate.log" "$OUT_DIR/full.log"
fi

# ── summary.md ────────────────────────────────────────────────────────────────

{
  echo "# ATDD Karate run — $TIMESTAMP"
  echo ""
  echo "**Base URL**: $BASE_URL"
  echo "**Features**: $idx · **Scenarios**: $TOTAL_SCENARIOS · **PASS**: $TOTAL_PASS · **FAIL**: $TOTAL_FAIL · **SKIP**: $TOTAL_SKIP"
  echo "**Exit code**: $EXIT_CODE"
  echo ""
  echo "## Features"
  echo ""
  echo "| # | Feature | Scenarios | PASS | FAIL | SKIP | Duration | Detail |"
  echo "|---|---|---|---|---|---|---|---|"
  for ROW in "${SUMMARY_ROWS[@]}"; do
    IFS=$'\t' read -r R_IDX R_NAME R_TOT R_PASS R_FAIL R_SKIP R_FILE R_DUR <<< "$ROW"
    echo "| $R_IDX | $R_NAME | $R_TOT | $R_PASS | $R_FAIL | $R_SKIP | $R_DUR | [→](features/$R_FILE) |"
  done
  echo ""
  echo "## Coverage"
  echo ""
  if [[ -n "$COVERAGE_REPORT_LABEL" ]]; then
    echo "_Source: $COVERAGE_REPORT_LABEL_"
    echo ""
  fi
  echo "| Package | Lines | Branches |"
  echo "|---|---|---|"
  if [[ -n "$COVERAGE_TABLE_ROWS" ]]; then
    printf '%s' "$COVERAGE_TABLE_ROWS"
  fi
  echo "| **Total** | **${TOTAL_LINE_COV}** | **${TOTAL_BRANCH_COV}** |"
  echo ""
  if [[ -f "$COVERAGE_DIR/index.html" ]]; then
    echo "[JaCoCo HTML report](coverage/index.html)"
  fi
} > "$OUT_DIR/summary.md"

# ── summary.txt ───────────────────────────────────────────────────────────────

{
  echo "ATDD Karate run: $TIMESTAMP"
  echo "Base URL:        $BASE_URL"
  echo "Features: $idx  Scenarios: $TOTAL_SCENARIOS  PASS: $TOTAL_PASS  FAIL: $TOTAL_FAIL  SKIP: $TOTAL_SKIP"
  echo "Exit code: $EXIT_CODE"
  printf '%s\n' "$(printf -- '─%.0s' {1..60})"
  for ROW in "${SUMMARY_ROWS[@]}"; do
    IFS=$'\t' read -r R_IDX R_NAME R_TOT R_PASS R_FAIL R_SKIP R_FILE R_DUR <<< "$ROW"
    printf '%-3s  %-40s  %3s total  %3s pass  %3s fail  %s\n' \
      "$R_IDX" "$R_NAME" "$R_TOT" "$R_PASS" "$R_FAIL" "$R_DUR"
  done
} > "$OUT_DIR/summary.txt"

# ── meta.json ─────────────────────────────────────────────────────────────────

HOSTNAME_VAL="$(hostname)"
jq -n \
  --arg run_id "$TIMESTAMP" \
  --arg host "$HOSTNAME_VAL" \
  --arg base_url "$BASE_URL" \
  --arg exit_code "$EXIT_CODE" \
  --argjson features "$idx" \
  --argjson scenarios "$TOTAL_SCENARIOS" \
  --argjson pass "$TOTAL_PASS" \
  --argjson fail "$TOTAL_FAIL" \
  --argjson skip "$TOTAL_SKIP" \
  '{run_id:$run_id, host:$host, base_url:$base_url, exit_code:($exit_code|tonumber), features:$features, scenarios:$scenarios, pass:$pass, fail:$fail, skip:$skip}' \
  > "$OUT_DIR/meta.json"

# ── symlink latest ────────────────────────────────────────────────────────────

LATEST_LINK="$ROOT/out/atdd-karate/latest"
rm -f "$LATEST_LINK"
ln -s "$TIMESTAMP" "$LATEST_LINK"

# ── Console summary ───────────────────────────────────────────────────────────

echo ""
echo "┌── ATDD Report ────────────────────────────────────────────┐"
printf "│  Features: %-3d  Scenarios: %-4d  PASS: %-3d  FAIL: %-3d     │\n" \
  "$idx" "$TOTAL_SCENARIOS" "$TOTAL_PASS" "$TOTAL_FAIL"
echo "└───────────────────────────────────────────────────────────┘"
echo ""
echo "  Report: $OUT_DIR"
echo "  View:   cat $OUT_DIR/summary.md"
