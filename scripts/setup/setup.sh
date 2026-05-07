#!/usr/bin/env bash
# setup.sh — Main orchestrator for the practica-entrevista toolchain setup
# Requires bash 4+
set -uo pipefail

SETUP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="${SETUP_DIR}/lib"
GROUPS_DIR="${SETUP_DIR}/groups"

# ── Bootstrap: check bash version ─────────────────────────────────────────────
if [[ "${BASH_VERSINFO[0]}" -lt 4 ]]; then
  # Try to find a newer bash before giving up
  for newer_bash in /opt/homebrew/bin/bash /usr/local/bin/bash; do
    if [[ -x "$newer_bash" ]] && "$newer_bash" -c 'exit $(( BASH_VERSINFO[0] < 4 ))' 2>/dev/null; then
      exec "$newer_bash" "$0" "$@"
    fi
  done
  printf 'ERROR: Bash 4+ required (found %s).\n' "$BASH_VERSION" >&2
  printf 'On macOS: brew install bash\n' >&2
  printf 'Then re-run: /opt/homebrew/bin/bash %s\n' "$0" >&2
  exit 1
fi

# ── Load libs ──────────────────────────────────────────────────────────────────
source "${LIB_DIR}/colors.sh"
source "${LIB_DIR}/logging.sh"
source "${LIB_DIR}/detect.sh"
source "${LIB_DIR}/installers.sh"
source "${LIB_DIR}/prompt.sh"

# ── Defaults ───────────────────────────────────────────────────────────────────
DRY_RUN=0
AUTO_YES=0
UPGRADE=0
VERIFY_ONLY=0
ONLY_GROUPS=()
SKIP_GROUPS=()

# ── Available groups ───────────────────────────────────────────────────────────
declare -A GROUP_FILES=(
  [core]="${GROUPS_DIR}/00-core.sh"
  [languages]="${GROUPS_DIR}/10-languages.sh"
  [containers]="${GROUPS_DIR}/20-containers.sh"
  [kubernetes]="${GROUPS_DIR}/30-kubernetes.sh"
  [aws]="${GROUPS_DIR}/40-aws.sh"
  [streaming]="${GROUPS_DIR}/50-streaming.sh"
  [observability]="${GROUPS_DIR}/60-observability.sh"
  [optional]="${GROUPS_DIR}/70-optional.sh"
)

GROUP_ORDER=(core languages containers kubernetes aws streaming observability optional)

# ── Help ───────────────────────────────────────────────────────────────────────
print_help() {
  cat <<EOF

${BOLD_CYAN}Risk Decision Platform — Setup${RESET}

${BOLD}USAGE${RESET}
  ./setup.sh [OPTIONS]

${BOLD}OPTIONS${RESET}
  (none)                Interactive mode. Installs missing tools with confirmation per group.
  --yes, -y             Auto-confirm all prompts (non-interactive).
  --dry-run             Show what would be installed without making any changes.
  --only <groups>       Install only the specified comma-separated groups.
                        Groups: core,languages,containers,kubernetes,aws,streaming,observability,optional
  --skip <groups>       Skip the specified comma-separated groups.
  --upgrade             Also upgrade already-installed tools that are below the required version.
  --verify              Run verify.sh only. No installs. Returns exit 1 if anything is missing.
  --help, -h            Show this help message.

${BOLD}EXAMPLES${RESET}
  ./setup.sh                          # interactive, install all missing
  ./setup.sh --yes                    # non-interactive, install all
  ./setup.sh --dry-run                # preview what would be installed
  ./setup.sh --only core,languages    # install only core and language tools
  ./setup.sh --skip optional          # install everything except optional group
  ./setup.sh --upgrade                # install missing + upgrade outdated
  ./setup.sh --verify                 # check status without installing

${BOLD}GROUPS AND TOOLS${RESET}
  core          bash 5+, curl, git, jq, yq, make              (6 tools)
  languages     java 25, maven 3.9+, go 1.23+, python 3.11+   (4 tools)
  containers    docker 24+, OrbStack/Desktop, compose v2       (3 tools)
  kubernetes    kubectl 1.28+, helm 3.13+, k3d 5.6+           (3 required + 2 optional)
  aws           aws CLI v2.15+, mc (MinIO client)             (2 tools)
  streaming     rpk v24.2+ (Redpanda CLI)                     (1 tool)
  observability otel-cli (opt), websocat, wscat               (3 tools)
  optional      obsidian, watch, htop, btop                   (4 tools, all optional)

${BOLD}NOTES${RESET}
  - macOS: Homebrew is required. Will offer to install it if missing.
  - Linux: apt/dnf/pacman detected automatically. sudo required for system packages.
  - WSL:   Docker should be managed via Docker Desktop Windows integration.
  - Windows native is not supported. Use WSL2.

EOF
}

# ── Parse args ─────────────────────────────────────────────────────────────────
parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --help|-h)
        print_help
        exit 0
        ;;
      --dry-run)
        DRY_RUN=1
        export DRY_RUN
        shift
        ;;
      --yes|-y)
        AUTO_YES=1
        export AUTO_YES
        shift
        ;;
      --upgrade)
        UPGRADE=1
        shift
        ;;
      --verify)
        VERIFY_ONLY=1
        shift
        ;;
      --only)
        [[ -z "${2:-}" ]] && { log_error "--only requires a comma-separated group list"; exit 1; }
        IFS=',' read -ra ONLY_GROUPS <<< "$2"
        shift 2
        ;;
      --skip)
        [[ -z "${2:-}" ]] && { log_error "--skip requires a comma-separated group list"; exit 1; }
        IFS=',' read -ra SKIP_GROUPS <<< "$2"
        shift 2
        ;;
      *)
        log_error "Unknown option: $1"
        print_help
        exit 1
        ;;
    esac
  done
}

# ── Group selection ────────────────────────────────────────────────────────────
should_run_group() {
  local group="$1"

  # If --only is set, only run listed groups
  if [[ ${#ONLY_GROUPS[@]} -gt 0 ]]; then
    for g in "${ONLY_GROUPS[@]}"; do
      [[ "$g" == "$group" ]] && return 0
    done
    return 1
  fi

  # If --skip is set, skip listed groups
  for g in "${SKIP_GROUPS[@]}"; do
    [[ "$g" == "$group" ]] && return 1
  done

  return 0
}

# ── Source group and run check/install ────────────────────────────────────────
run_group_check() {
  local group="$1"
  local file="${GROUP_FILES[$group]}"

  if [[ ! -f "$file" ]]; then
    log_warn "Group file not found: $file"
    return
  fi

  # Source in subshell context to avoid polluting globals
  # But we need MISSING/OUTDATED counts, so source directly
  # Reset per-group state before sourcing
  MISSING_TOOLS=()
  OUTDATED_TOOLS=()

  # shellcheck source=/dev/null
  source "$file"
  group_check
}

run_group_install() {
  local group="$1"
  local file="${GROUP_FILES[$group]}"

  if [[ ! -f "$file" ]]; then return; fi

  # shellcheck source=/dev/null
  source "$file"
  group_install "$UPGRADE"
}

# ── Summary line for a group (after check) ─────────────────────────────────────
# Returns counts; must be called after run_group_check populates MISSING_TOOLS/OUTDATED_TOOLS
count_group_issues() {
  echo "${#MISSING_TOOLS[@]}:${#OUTDATED_TOOLS[@]}"
}

# ── Homebrew bootstrap (macOS) ────────────────────────────────────────────────
ensure_homebrew() {
  local os
  os="$(detect_os)"
  [[ "$os" != "macos" ]] && return 0

  if ! has_command brew; then
    log_warn "Homebrew is not installed. It's required for macOS tool installation."
    if confirm "Install Homebrew now?" "Y"; then
      install_homebrew
    else
      log_error "Homebrew required on macOS. Aborting."
      exit 1
    fi
  fi
}

# ── Main ───────────────────────────────────────────────────────────────────────
main() {
  parse_args "$@"

  # If --verify, delegate to verify.sh
  if [[ "$VERIFY_ONLY" == "1" ]]; then
    exec "${SETUP_DIR}/verify.sh"
  fi

  section_header "Risk Decision Platform — Setup"

  if [[ "$DRY_RUN" == "1" ]]; then
    printf '  %s[DRY RUN]%s No changes will be made.\n\n' "${BOLD_YELLOW}" "${RESET}"
  fi

  # ── System info ──────────────────────────────────────────────────────────────
  log_step "Detecting system..."
  print_system_info

  # ── Homebrew check ────────────────────────────────────────────────────────────
  [[ "$DRY_RUN" != "1" ]] && ensure_homebrew

  # ── Detection pass ────────────────────────────────────────────────────────────
  log_step "Detecting toolchain..."

  declare -A group_missing_count
  declare -A group_outdated_count
  declare -a groups_with_issues=()
  declare -a groups_to_run=()

  for group in "${GROUP_ORDER[@]}"; do
    if ! should_run_group "$group"; then
      continue
    fi
    groups_to_run+=("$group")

    # Source and check
    MISSING_TOOLS=()
    OUTDATED_TOOLS=()
    # shellcheck source=/dev/null
    source "${GROUP_FILES[$group]}"
    group_check

    group_missing_count[$group]="${#MISSING_TOOLS[@]}"
    group_outdated_count[$group]="${#OUTDATED_TOOLS[@]}"

    local issues=$(( ${group_missing_count[$group]} + ${group_outdated_count[$group]} ))
    [[ $issues -gt 0 ]] && groups_with_issues+=("$group")
  done

  # ── Summary ──────────────────────────────────────────────────────────────────
  local total_missing=0 total_outdated=0
  for group in "${groups_to_run[@]}"; do
    total_missing=$(( total_missing + group_missing_count[$group] ))
    total_outdated=$(( total_outdated + group_outdated_count[$group] ))
  done

  printf '\n'
  divider "─" 60
  printf '\n  '

  if [[ $total_missing -eq 0 ]] && [[ $total_outdated -eq 0 ]]; then
    printf '%s All tools present and up to date.%s\n' "${BOLD_GREEN}" "${RESET}"
    printf '\n'
    exit 0
  fi

  local missing_names=()
  for group in "${groups_with_issues[@]}"; do
    # Use cached counts — MISSING/OUTDATED arrays were populated in the detection loop above
    # Re-source silently to rebuild the arrays without printing
    MISSING_TOOLS=()
    OUTDATED_TOOLS=()
    # shellcheck source=/dev/null
    source "${GROUP_FILES[$group]}" 2>/dev/null
    group_check >/dev/null 2>&1
    for t in "${MISSING_TOOLS[@]}"; do missing_names+=("$t"); done
  done

  printf '%sSummary%s: %d missing' "${BOLD}" "${RESET}" "$total_missing"
  [[ $total_outdated -gt 0 ]] && printf ', %d outdated' "$total_outdated"
  printf '.'
  if [[ ${#missing_names[@]} -gt 0 ]]; then
    printf ' (%s)' "${missing_names[*]}"
  fi
  printf '\n\n'

  # ── Dry-run: just show what would be done ─────────────────────────────────────
  if [[ "$DRY_RUN" == "1" ]]; then
    printf '  %s[dry-run]%s Would install the above tools.\n' "${BOLD_YELLOW}" "${RESET}"
    printf '  Run without --dry-run to install.\n\n'
    exit 0
  fi

  # ── Install pass ─────────────────────────────────────────────────────────────
  for group in "${groups_to_run[@]}"; do
    local issues=$(( group_missing_count[$group] + group_outdated_count[$group] ))
    [[ "$UPGRADE" == "0" ]] && issues="${group_missing_count[$group]}"
    [[ $issues -eq 0 ]] && continue

    # Re-source and run install
    MISSING_TOOLS=()
    OUTDATED_TOOLS=()
    # shellcheck source=/dev/null
    source "${GROUP_FILES[$group]}"
    group_check 2>/dev/null  # repopulate MISSING/OUTDATED silently
    group_install "$UPGRADE"
  done

  # ── Final summary ─────────────────────────────────────────────────────────────
  printf '\n'
  print_summary
  release_sudo

  if [[ ${#FAILED_TOOLS[@]} -gt 0 ]]; then
    exit 1
  fi
  exit 0
}

main "$@"
