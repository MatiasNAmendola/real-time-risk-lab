#!/usr/bin/env bash
# colors.sh — ANSI color helpers
# Safe to source multiple times

[[ -n "${_COLORS_SH_LOADED:-}" ]] && return 0
_COLORS_SH_LOADED=1

# Detect color support
if [[ -t 1 ]] && [[ "${TERM:-}" != "dumb" ]] && [[ "${NO_COLOR:-}" != "1" ]]; then
  _COLOR_SUPPORT=1
else
  _COLOR_SUPPORT=0
fi

_color() {
  [[ "$_COLOR_SUPPORT" == "1" ]] && printf '%b' "$1" || true
}

# Reset
RESET=$(_color '\033[0m')

# Regular colors
RED=$(_color '\033[0;31m')
GREEN=$(_color '\033[0;32m')
YELLOW=$(_color '\033[0;33m')
BLUE=$(_color '\033[0;34m')
MAGENTA=$(_color '\033[0;35m')
CYAN=$(_color '\033[0;36m')
WHITE=$(_color '\033[0;37m')
GRAY=$(_color '\033[0;90m')

# Bold colors
BOLD=$(_color '\033[1m')
BOLD_RED=$(_color '\033[1;31m')
BOLD_GREEN=$(_color '\033[1;32m')
BOLD_YELLOW=$(_color '\033[1;33m')
BOLD_BLUE=$(_color '\033[1;34m')
BOLD_CYAN=$(_color '\033[1;36m')
BOLD_WHITE=$(_color '\033[1;37m')

# Status symbols
SYM_OK="${GREEN}✓${RESET}"
SYM_FAIL="${RED}✗${RESET}"
SYM_WARN="${YELLOW}⚠${RESET}"
SYM_SKIP="${GRAY}-${RESET}"
SYM_ARROW="${CYAN}→${RESET}"
SYM_BULLET="${GRAY}•${RESET}"

# Divider
divider() {
  local char="${1:-━}"
  local width="${2:-60}"
  printf '%s' "${BOLD}"
  printf '%0.s'"$char" $(seq 1 "$width")
  printf '%s\n' "${RESET}"
}

# Section header
section_header() {
  local title="$1"
  printf '\n%s━━━ %s ━━━%s\n\n' "${BOLD_CYAN}" "$title" "${RESET}"
}
