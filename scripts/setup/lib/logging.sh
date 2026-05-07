#!/usr/bin/env bash
# logging.sh — Structured logging helpers

[[ -n "${_LOGGING_SH_LOADED:-}" ]] && return 0
_LOGGING_SH_LOADED=1

SCRIPT_DIR_LOGGING="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=colors.sh
source "${SCRIPT_DIR_LOGGING}/colors.sh"

# Log level: 0=debug, 1=info, 2=warn, 3=error
LOG_LEVEL="${LOG_LEVEL:-1}"

log_debug() {
  [[ "$LOG_LEVEL" -le 0 ]] || return 0
  printf '%s[DEBUG]%s %s\n' "${GRAY}" "${RESET}" "$*" >&2
}

log_info() {
  [[ "$LOG_LEVEL" -le 1 ]] || return 0
  printf '  %s\n' "$*"
}

log_warn() {
  [[ "$LOG_LEVEL" -le 2 ]] || return 0
  printf '  %s[WARN]%s  %s\n' "${BOLD_YELLOW}" "${RESET}" "$*" >&2
}

log_error() {
  printf '  %s[ERROR]%s %s\n' "${BOLD_RED}" "${RESET}" "$*" >&2
}

log_step() {
  printf '\n  %s▸%s %s%s%s\n' "${BOLD_CYAN}" "${RESET}" "${BOLD}" "$*" "${RESET}"
}

log_ok() {
  printf '    %s %s\n' "${SYM_OK}" "$*"
}

log_fail() {
  printf '    %s %s\n' "${SYM_FAIL}" "$*"
}

log_skip() {
  printf '    %s %s\n' "${SYM_SKIP}" "$*"
}

log_arrow() {
  printf '    %s %s\n' "${SYM_ARROW}" "$*"
}

# Tool status line: padded tool name + version + status
# Usage: log_tool_status <name> <version_or_"not installed"> <status: ok|missing|outdated|optional>
log_tool_status() {
  local name="$1"
  local version="$2"
  local status="$3"
  local pad=14

  printf '    '
  case "$status" in
    ok)
      printf '%s' "${SYM_OK}"
      printf ' %-*s ' "$pad" "$name"
      printf '%s%-20s%s %s\n' "${GREEN}" "$version" "${RESET}" "OK"
      ;;
    missing)
      printf '%s' "${SYM_FAIL}"
      printf ' %-*s ' "$pad" "$name"
      printf '%s%-20s%s\n' "${RED}" "not installed" "${RESET}"
      ;;
    outdated)
      printf '%s' "${SYM_WARN}"
      printf ' %-*s ' "$pad" "$name"
      printf '%s%-20s%s %s\n' "${YELLOW}" "$version" "${RESET}" "OUTDATED"
      ;;
    optional)
      printf '%s' "${SYM_SKIP}"
      printf ' %-*s ' "$pad" "$name"
      printf '%s%-20s%s\n' "${GRAY}" "optional, not installed" "${RESET}"
      ;;
    optional_ok)
      printf '%s' "${SYM_OK}"
      printf ' %-*s ' "$pad" "$name"
      printf '%s%-20s%s %s\n' "${GREEN}" "$version" "${RESET}" "OK (optional)"
      ;;
    *)
      printf '? %-*s %s\n' "$pad" "$name" "$version"
      ;;
  esac
}

# Accumulate failures
FAILED_TOOLS=()
INSTALLED_TOOLS=()
SKIPPED_TOOLS=()

record_failure() {
  FAILED_TOOLS+=("$1: $2")
}

record_install() {
  INSTALLED_TOOLS+=("$1")
}

record_skip() {
  SKIPPED_TOOLS+=("$1")
}

print_summary() {
  printf '\n'
  divider "─" 60
  printf '\n  %sSummary%s\n\n' "${BOLD}" "${RESET}"

  if [[ ${#INSTALLED_TOOLS[@]} -gt 0 ]]; then
    printf '  %sInstalled%s (%d):\n' "${BOLD_GREEN}" "${RESET}" "${#INSTALLED_TOOLS[@]}"
    for t in "${INSTALLED_TOOLS[@]}"; do
      printf '    %s %s\n' "${SYM_OK}" "$t"
    done
    printf '\n'
  fi

  if [[ ${#FAILED_TOOLS[@]} -gt 0 ]]; then
    printf '  %sFailed%s (%d):\n' "${BOLD_RED}" "${RESET}" "${#FAILED_TOOLS[@]}"
    for t in "${FAILED_TOOLS[@]}"; do
      printf '    %s %s\n' "${SYM_FAIL}" "$t"
    done
    printf '\n'
  fi

  if [[ ${#SKIPPED_TOOLS[@]} -gt 0 ]]; then
    printf '  %sSkipped%s (%d): %s\n' "${GRAY}" "${RESET}" "${#SKIPPED_TOOLS[@]}" "${SKIPPED_TOOLS[*]}"
    printf '\n'
  fi
}
