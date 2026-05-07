#!/usr/bin/env bash
# prompt.sh — Interactive prompts with Y/n defaults

[[ -n "${_PROMPT_SH_LOADED:-}" ]] && return 0
_PROMPT_SH_LOADED=1

SCRIPT_DIR_PROMPT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=colors.sh
source "${SCRIPT_DIR_PROMPT}/colors.sh"

# Global auto-yes flag (set from main script)
AUTO_YES="${AUTO_YES:-0}"

# confirm <question> [default: Y|n]
# Returns 0 if user says yes, 1 if no
confirm() {
  local question="$1"
  local default="${2:-Y}"

  # Auto-yes mode
  if [[ "$AUTO_YES" == "1" ]]; then
    printf '  %s [%s] %s(auto-yes)%s\n' "$question" "$default" "${GRAY}" "${RESET}"
    return 0
  fi

  local prompt
  if [[ "${default^^}" == "Y" ]]; then
    prompt="${BOLD}[Y/n]${RESET}"
  else
    prompt="${BOLD}[y/N]${RESET}"
  fi

  printf '\n  %s %s ' "$question" "$prompt"

  local answer
  read -r answer

  # Empty answer => use default
  [[ -z "$answer" ]] && answer="$default"

  case "${answer^^}" in
    Y|YES) return 0 ;;
    N|NO)  return 1 ;;
    *)
      printf '  Invalid input. Assuming %s.\n' "$default"
      [[ "${default^^}" == "Y" ]] && return 0 || return 1
      ;;
  esac
}

# confirm_or_skip <question> <skip_message>
# Returns 0 to proceed, 1 to skip (without error)
confirm_or_skip() {
  local question="$1"
  local skip_message="${2:-Skipping.}"
  if confirm "$question"; then
    return 0
  else
    printf '  %s\n' "$skip_message"
    return 1
  fi
}

# select_option <prompt> <options...>
# Prints selected index (0-based) to stdout
# Returns 0 on valid selection
select_option() {
  local prompt="$1"
  shift
  local options=("$@")

  printf '\n  %s\n' "$prompt"
  for i in "${!options[@]}"; do
    printf '    %s[%d]%s %s\n' "${BOLD_CYAN}" "$((i+1))" "${RESET}" "${options[$i]}"
  done
  printf '  Choice [1-%d]: ' "${#options[@]}"

  local choice
  read -r choice

  if [[ "$choice" =~ ^[0-9]+$ ]] && (( choice >= 1 && choice <= ${#options[@]} )); then
    echo $((choice - 1))
    return 0
  else
    printf '  Invalid choice. Using option 1.\n'
    echo 0
    return 0
  fi
}
