#!/usr/bin/env bash
# verify.sh — Check all tools without installing anything
# Exit codes: 0=all OK, 1=missing/outdated, 2=unsupported OS
set -euo pipefail

SETUP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="${SETUP_DIR}/lib"

source "${LIB_DIR}/colors.sh"
source "${LIB_DIR}/logging.sh"
source "${LIB_DIR}/detect.sh"

# ── Detect bash version ────────────────────────────────────────────────────────
if [[ "${BASH_VERSINFO[0]}" -lt 4 ]]; then
  printf 'ERROR: Bash 4+ required (found %s). On macOS: brew install bash\n' "$BASH_VERSION" >&2
  exit 2
fi

# ── OS check ───────────────────────────────────────────────────────────────────
OS="$(detect_os)"
if [[ "$OS" == "unknown" ]]; then
  log_error "Unsupported OS. This script supports macOS and Linux only."
  exit 2
fi

# ── Tool definitions ───────────────────────────────────────────────────────────
# Format: group|name|version_cmd|version_regex|min_version|optional(0/1)

declare -a VERIFY_TOOLS=(
  # core (6 tools)
  "core|bash|bash --version|GNU bash, version ([0-9]+\.[0-9]+\.[0-9]+)|5.0.0|0"
  "core|curl|curl --version|curl ([0-9]+\.[0-9]+\.[0-9]+)|7.0.0|0"
  "core|git|git --version|git version ([0-9]+\.[0-9]+\.[0-9]+)|2.30.0|0"
  "core|jq|jq --version|jq-([0-9]+\.[0-9]+)|1.6.0|0"
  "core|yq|yq --version|version v([0-9]+\.[0-9]+\.[0-9]+)|4.40.0|0"
  "core|make|make --version|GNU Make ([0-9]+\.[0-9]+)|3.80|0"
  # languages (5 tools — node via fnm is optional, only required for TS SDK)
  "languages|java|java -version 2>&1|version.\"([0-9]+)|25|0"
  "languages|mvn|mvn --version|Apache Maven ([0-9]+\.[0-9]+\.[0-9]+)|3.9.0|0"
  "languages|go|go version|go([0-9]+\.[0-9]+\.[0-9]+)|1.23.0|0"
  "languages|python3|python3 --version|Python ([0-9]+\.[0-9]+\.[0-9]+)|3.11.0|0"
  "languages|node-toolchain|true|()|0|1"
  # containers (3 tools)
  "containers|docker|docker --version|Docker version ([0-9]+\.[0-9]+\.[0-9]+)|24.0.0|0"
  "containers|docker-compose-v2|docker compose version|v([0-9]+\.[0-9]+\.[0-9]+)|2.20.0|0"
  # kubernetes: kubectl/helm are enough for reading/manifests. k3d/kustomize/argocd
  # are optional for the local-k8s deep dive and must not block the core demo verify.
  "kubernetes|kubectl|kubectl version --client --short 2>/dev/null || kubectl version --client 2>&1|v([0-9]+\.[0-9]+\.[0-9]+)|1.28.0|0"
  "kubernetes|helm|helm version --short|v([0-9]+\.[0-9]+\.[0-9]+)|3.13.0|0"
  "kubernetes|k3d|k3d version|k3d version v([0-9]+\.[0-9]+\.[0-9]+)|5.6.0|1"
  "kubernetes|argocd|argocd version --client 2>&1|argocd: v([0-9]+\.[0-9]+\.[0-9]+)|2.0.0|1"
  "kubernetes|kustomize|kustomize version|v([0-9]+\.[0-9]+\.[0-9]+)|4.0.0|1"
  # aws: AWS CLI is used by scripts; MinIO mc/mcli is a convenience admin client.
  "aws|aws|aws --version|aws-cli/([0-9]+\.[0-9]+\.[0-9]+)|2.15.0|0"
  "aws|mc|mc --version 2>&1|RELEASE\.([0-9]{4})-([0-9]{2})|2024.0|1"
  # streaming (1 tool)
  "streaming|rpk|rpk version|v([0-9]+\.[0-9]+\.[0-9]+)|24.2.0|0"
  # observability: optional CLI helpers. The services export OTEL without these binaries.
  "observability|otel-cli|otel-cli version 2>&1|v([0-9]+\.[0-9]+\.[0-9]+)|0.0.1|1"
  "observability|websocat|websocat --version 2>&1|websocat ([0-9]+\.[0-9]+\.[0-9]+)|1.0.0|1"
)

# ── State tracking ─────────────────────────────────────────────────────────────
declare -A GROUP_TOTAL
declare -A GROUP_OK
declare -A GROUP_MISSING
declare -A GROUP_MISSING_NAMES

TOTAL_MISSING=0
TOTAL_OUTDATED=0

# Init groups
for g in core languages containers kubernetes aws streaming observability optional; do
  GROUP_TOTAL[$g]=0
  GROUP_OK[$g]=0
  GROUP_MISSING[$g]=0
  GROUP_MISSING_NAMES[$g]=""
done

# ── Verify each tool ───────────────────────────────────────────────────────────

verify_tool() {
  local group="$1"
  local name="$2"
  local version_cmd="$3"
  local version_regex="$4"
  local min_version="$5"
  local optional="$6"

  GROUP_TOTAL[$group]=$(( ${GROUP_TOTAL[$group]:-0} + 1 ))

  # Special check for Node.js toolchain. The user may manage Node via fnm
  # (preferred), nvm, asdf, Volta, or system install. We accept any of:
  #   1. fnm present  → activate `fnm env` and verify node/npm resolve
  #   2. node + npm already on PATH (any other manager)
  if [[ "$name" == "node-toolchain" ]]; then
    local node_ok=0
    local detail=""
    if has_command fnm; then
      # Activate in a subshell so we don't pollute the verify script env
      if (eval "$(fnm env --shell bash 2>/dev/null || fnm env 2>/dev/null)"; \
          command -v node >/dev/null && command -v npm >/dev/null) 2>/dev/null; then
        local nv
        nv="$(eval "$(fnm env --shell bash 2>/dev/null || fnm env 2>/dev/null)"; node --version 2>/dev/null || true)"
        detail="fnm + node ${nv}"
        node_ok=1
      else
        detail="fnm present but no LTS installed (run: fnm install --lts)"
      fi
    elif has_command node && has_command npm; then
      detail="node $(node --version 2>/dev/null) (no fnm — consider: brew install fnm)"
      node_ok=1
    fi

    if [[ $node_ok -eq 1 ]]; then
      GROUP_OK[$group]=$(( ${GROUP_OK[$group]:-0} + 1 ))
    else
      # Optional, so it never blocks the verify exit code
      GROUP_MISSING[$group]=$(( ${GROUP_MISSING[$group]:-0} + 1 ))
      GROUP_MISSING_NAMES[$group]+=" node-toolchain(${detail:-not-found; brew install fnm})"
    fi
    return
  fi

  # Special check for docker compose v2 (plugin not standalone)
  if [[ "$name" == "docker-compose-v2" ]]; then
    if docker compose version &>/dev/null 2>&1; then
      local output version
      output="$(docker compose version 2>&1)"
      version="$(extract_version "$output" 'v([0-9]+\.[0-9]+\.[0-9]+)')"
      if version_at_least "$version" "$min_version"; then
        GROUP_OK[$group]=$(( ${GROUP_OK[$group]:-0} + 1 ))
      else
        GROUP_MISSING[$group]=$(( ${GROUP_MISSING[$group]:-0} + 1 ))
        GROUP_MISSING_NAMES[$group]+=" compose-v2"
        TOTAL_OUTDATED=$(( TOTAL_OUTDATED + 1 ))
      fi
    else
      GROUP_MISSING[$group]=$(( ${GROUP_MISSING[$group]:-0} + 1 ))
      GROUP_MISSING_NAMES[$group]+=" compose-v2"
      if [[ "$optional" == "0" ]]; then TOTAL_MISSING=$(( TOTAL_MISSING + 1 )); fi
    fi
    return
  fi

  # Check if mc is minio (not midnight commander)
  if [[ "$name" == "mc" ]]; then
    if has_command mc && mc --version 2>&1 | grep -qi minio; then
      GROUP_OK[$group]=$(( ${GROUP_OK[$group]:-0} + 1 ))
      return
    elif has_command mcli; then
      GROUP_OK[$group]=$(( ${GROUP_OK[$group]:-0} + 1 ))
      return
    else
      GROUP_MISSING[$group]=$(( ${GROUP_MISSING[$group]:-0} + 1 ))
      GROUP_MISSING_NAMES[$group]+=" mc(optional)"
      if [[ "$optional" == "0" ]]; then TOTAL_MISSING=$(( TOTAL_MISSING + 1 )); fi
      return
    fi
  fi

  # Check java major version. Prefer $JAVA_HOME/bin/java over `which java`
  # so verify reflects the JDK Gradle/Maven actually pick up.
  if [[ "$name" == "java" ]]; then
    local java_bin=""
    if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
      java_bin="${JAVA_HOME}/bin/java"
    elif has_command java; then
      java_bin="$(command -v java)"
    fi

    if [[ -z "$java_bin" ]]; then
      GROUP_MISSING[$group]=$(( ${GROUP_MISSING[$group]:-0} + 1 ))
      GROUP_MISSING_NAMES[$group]+=" java"
      if [[ "$optional" == "0" ]]; then TOTAL_MISSING=$(( TOTAL_MISSING + 1 )); fi
      return
    fi

    local output
    output="$("$java_bin" -version 2>&1 || true)"
    if [[ "$output" =~ version[[:space:]]\"([0-9]+) ]]; then
      local major="${BASH_REMATCH[1]}"
      # Project rule R1 mandates Java 25 LTS, but Java 21 LTS is accepted
      # as a fallback so devs on Temurin 21 don't get false-positives
      # while waiting for a 25 install. Anything <21 is still flagged.
      if [[ "$major" -ge 21 ]]; then
        GROUP_OK[$group]=$(( ${GROUP_OK[$group]:-0} + 1 ))
      else
        GROUP_MISSING[$group]=$(( ${GROUP_MISSING[$group]:-0} + 1 ))
        GROUP_MISSING_NAMES[$group]+=" java(v${major})"
        TOTAL_OUTDATED=$(( TOTAL_OUTDATED + 1 ))
      fi
    else
      GROUP_MISSING[$group]=$(( ${GROUP_MISSING[$group]:-0} + 1 ))
      GROUP_MISSING_NAMES[$group]+=" java"
      if [[ "$optional" == "0" ]]; then TOTAL_MISSING=$(( TOTAL_MISSING + 1 )); fi
    fi
    return
  fi

  local binary="${name}"
  if ! has_command "$binary"; then
    GROUP_MISSING[$group]=$(( ${GROUP_MISSING[$group]:-0} + 1 ))
    if [[ "$optional" == "0" ]]; then
      GROUP_MISSING_NAMES[$group]+=" $name"
      TOTAL_MISSING=$(( TOTAL_MISSING + 1 ))
    else
      GROUP_MISSING_NAMES[$group]+=" ${name}(optional)"
    fi
    return
  fi

  local version_output version
  version_output="$(eval "$version_cmd" 2>&1 || true)"
  version="$(extract_version "$version_output" "$version_regex")"

  if [[ -z "$version" ]]; then
    # Can't detect version but binary exists — count as OK with warning
    GROUP_OK[$group]=$(( ${GROUP_OK[$group]:-0} + 1 ))
    return
  fi

  if version_at_least "$version" "$min_version"; then
    GROUP_OK[$group]=$(( ${GROUP_OK[$group]:-0} + 1 ))
  else
    GROUP_MISSING[$group]=$(( ${GROUP_MISSING[$group]:-0} + 1 ))
    if [[ "$optional" == "0" ]]; then
      GROUP_MISSING_NAMES[$group]+=" ${name}(${version}<${min_version})"
      TOTAL_OUTDATED=$(( TOTAL_OUTDATED + 1 ))
    else
      GROUP_MISSING_NAMES[$group]+=" ${name}(optional:${version}<${min_version})"
    fi
  fi
}

# ── Main ───────────────────────────────────────────────────────────────────────

main() {
  section_header "Risk Decision Platform — Verify"

  printf '  Running verification (no installs)...\n\n'

  for spec in "${VERIFY_TOOLS[@]}"; do
    IFS='|' read -r group name version_cmd version_regex min_version optional <<< "$spec"
    verify_tool "$group" "$name" "$version_cmd" "$version_regex" "$min_version" "$optional"
  done

  # ── Print results per group ────────────────────────────────────────────────

  printf '  %-18s %s\n' "Group" "Status"
  printf '  %-18s %s\n' "─────────────────" "──────────────────────────────────"

  local any_fail=0

  for g in core languages containers kubernetes aws streaming observability optional; do
    local total="${GROUP_TOTAL[$g]:-0}"
    local ok="${GROUP_OK[$g]:-0}"
    local missing="${GROUP_MISSING[$g]:-0}"
    local missing_names="${GROUP_MISSING_NAMES[$g]:-}"

    if [[ "$g" == "optional" ]]; then
      printf '  %-18s %s skipped\n' "$g" "${GRAY}-${RESET}"
      continue
    fi

    if [[ $total -eq 0 ]]; then
      printf '  %-18s %s not checked\n' "$g" "${GRAY}-${RESET}"
      continue
    fi

    # Optional tools are shown for transparency but do not fail core verification.
    # Only names without an `(optional...)` marker are blocking.
    local blocking_names
    blocking_names="$(printf '%s' "$missing_names" | sed -E 's/ ?[^ ]+\(optional[^)]*\)//g')"
    if [[ $missing -eq 0 ]]; then
      printf '  %-18s %s %d/%d\n' "$g" "${SYM_OK}" "$ok" "$total"
    elif [[ -z "${blocking_names// /}" ]]; then
      printf '  %-18s %s %d/%d   %s(optional:%s)%s\n' \
        "$g" "${SYM_OK}" "$ok" "$total" \
        "${GRAY}" "$missing_names" "${RESET}"
    else
      printf '  %-18s %s %d/%d   %s(missing:%s)%s\n' \
        "$g" "${SYM_FAIL}" "$ok" "$total" \
        "${YELLOW}" "$blocking_names" "${RESET}"
      any_fail=1
    fi
  done

  printf '\n'

  local total_issues=$(( TOTAL_MISSING + TOTAL_OUTDATED ))
  if [[ $total_issues -eq 0 ]]; then
    printf '  %s All tools OK.\n\n' "${SYM_OK}"
    return 0
  else
    printf '  %d missing/outdated item(s). Run %s./setup.sh%s to install.\n\n' \
      "$total_issues" "${BOLD_CYAN}" "${RESET}"
    return 1
  fi
}

main "$@"
