#!/usr/bin/env bash
# .ai/scripts/verify-primitives.sh
# Verifica que el sistema de primitivas esta completo y valido.
# Exit 0: todo OK. Exit 1: algo falta o esta mal.

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
AI_DIR="$(dirname "$SCRIPT_DIR")"
REPO_ROOT="$(dirname "$AI_DIR")"

ERRORS=0
WARNINGS=0

red()    { printf '\033[0;31m%s\033[0m\n' "$*"; }
green()  { printf '\033[0;32m%s\033[0m\n' "$*"; }
yellow() { printf '\033[0;33m%s\033[0m\n' "$*"; }
info()   { printf '  %s\n' "$*"; }

check_count() {
    local dir="$1"
    local min="$2"
    local label="$3"
    local count
    count=$(find "$dir" -maxdepth 1 -name "*.md" 2>/dev/null | wc -l | tr -d ' ')
    if [ "$count" -lt "$min" ]; then
        red "FAIL: $label: found $count, need >= $min"
        ERRORS=$((ERRORS + 1))
    else
        green "OK:   $label: $count files (min $min)"
    fi
}

check_file() {
    local file="$1"
    local label="$2"
    if [ -f "$file" ]; then
        green "OK:   $label exists"
    else
        red "FAIL: $label missing: $file"
        ERRORS=$((ERRORS + 1))
    fi
}

check_dir() {
    local dir="$1"
    local label="$2"
    if [ -d "$dir" ]; then
        green "OK:   $label exists"
    else
        red "FAIL: $label missing: $dir"
        ERRORS=$((ERRORS + 1))
    fi
}

check_frontmatter() {
    local dir="$1"
    local label="$2"
    local failed=0
    for f in "$dir"/*.md; do
        [ -f "$f" ] || continue
        if ! grep -q "^---" "$f" 2>/dev/null; then
            yellow "WARN: missing frontmatter in $(basename "$f")"
            WARNINGS=$((WARNINGS + 1))
            failed=$((failed + 1))
        fi
    done
    if [ "$failed" -eq 0 ]; then
        green "OK:   $label frontmatter valid"
    fi
}

check_adapter() {
    local ide="$1"
    local dir="$AI_DIR/adapters/$ide"
    if [ -d "$dir" ]; then
        local has_readme=0
        local has_install=0
        [ -f "$dir/README.md" ] && has_readme=1
        [ -f "$dir/install.sh" ] && has_install=1
        if [ "$has_readme" -eq 1 ] && [ "$has_install" -eq 1 ]; then
            green "OK:   adapter/$ide (README.md + install.sh)"
        else
            [ "$has_readme" -eq 0 ] && { red "FAIL: adapter/$ide missing README.md"; ERRORS=$((ERRORS + 1)); }
            [ "$has_install" -eq 0 ] && { red "FAIL: adapter/$ide missing install.sh"; ERRORS=$((ERRORS + 1)); }
        fi
    else
        red "FAIL: adapter/$ide directory missing"
        ERRORS=$((ERRORS + 1))
    fi
}

echo ""
echo "=== verify-primitives.sh ==="
echo "Repo: $REPO_ROOT"
echo ""

echo "--- Counts ---"
check_count "$AI_DIR/primitives/skills"    25 "skills"
check_count "$AI_DIR/primitives/rules"     12 "rules"
check_count "$AI_DIR/primitives/workflows"  8 "workflows"
check_count "$AI_DIR/primitives/hooks"      5 "hooks"

echo ""
echo "--- Frontmatter ---"
check_frontmatter "$AI_DIR/primitives/skills"    "skills"
check_frontmatter "$AI_DIR/primitives/rules"     "rules"
check_frontmatter "$AI_DIR/primitives/workflows" "workflows"
check_frontmatter "$AI_DIR/primitives/hooks"     "hooks"

echo ""
echo "--- Adapters ---"
for ide in claude-code cursor windsurf copilot codex antigravity opencode kiro; do
    check_adapter "$ide"
done

echo ""
echo "--- Root entrypoints ---"
check_file "$REPO_ROOT/AGENTS.md"                        "AGENTS.md"
check_file "$REPO_ROOT/CLAUDE.md"                        "CLAUDE.md"
check_file "$REPO_ROOT/.windsurfrules"                   ".windsurfrules"
check_file "$REPO_ROOT/.github/copilot-instructions.md"  ".github/copilot-instructions.md"

echo ""
echo "--- Cursor rules ---"
check_dir  "$REPO_ROOT/.cursor/rules"                    ".cursor/rules/"
check_file "$REPO_ROOT/.cursor/rules/00-project.mdc"     ".cursor/rules/00-project.mdc"
check_file "$REPO_ROOT/.cursor/rules/10-architecture.mdc" ".cursor/rules/10-architecture.mdc"
check_file "$REPO_ROOT/.cursor/rules/20-testing.mdc"     ".cursor/rules/20-testing.mdc"

echo ""
echo "--- Context files ---"
check_file "$AI_DIR/context/architecture.md"    "context/architecture.md"
check_file "$AI_DIR/context/poc-inventory.md"   "context/poc-inventory.md"
check_file "$AI_DIR/context/decisions-log.md"   "context/decisions-log.md"
check_file "$AI_DIR/context/glossary.md"        "context/glossary.md"
check_file "$AI_DIR/context/stack.md"           "context/stack.md"
check_file "$AI_DIR/context/engram.md"          "context/engram.md"
check_file "$AI_DIR/context/exploration-state.md" "context/exploration-state.md"

echo ""
echo "--- .ai/README.md ---"
check_file "$AI_DIR/README.md" ".ai/README.md"

echo ""
echo "=== Summary ==="
if [ "$ERRORS" -eq 0 ] && [ "$WARNINGS" -eq 0 ]; then
    green "All checks passed. Primitives system is complete."
elif [ "$ERRORS" -eq 0 ]; then
    yellow "Passed with $WARNINGS warnings."
else
    red "FAILED: $ERRORS errors, $WARNINGS warnings."
    echo ""
    echo "Fix the errors above and re-run this script."
    exit 1
fi
