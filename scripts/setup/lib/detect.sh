#!/usr/bin/env bash
# detect.sh — OS/arch detection + version helpers

[[ -n "${_DETECT_SH_LOADED:-}" ]] && return 0
_DETECT_SH_LOADED=1

SCRIPT_DIR_DETECT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=logging.sh
source "${SCRIPT_DIR_DETECT}/logging.sh"

# ── OS detection ──────────────────────────────────────────────────────────────

detect_os() {
  local uname_out
  uname_out="$(uname -s)"
  case "$uname_out" in
    Darwin) echo "macos" ;;
    Linux)
      if grep -qi microsoft /proc/version 2>/dev/null; then
        echo "wsl"
      else
        echo "linux"
      fi
      ;;
    *) echo "unknown" ;;
  esac
}

detect_arch() {
  local arch
  arch="$(uname -m)"
  case "$arch" in
    x86_64|amd64) echo "amd64" ;;
    arm64|aarch64) echo "arm64" ;;
    *) echo "$arch" ;;
  esac
}

detect_linux_distro() {
  if [[ -f /etc/os-release ]]; then
    # shellcheck source=/dev/null
    . /etc/os-release
    echo "${ID:-unknown}"
  elif command -v lsb_release &>/dev/null; then
    lsb_release -si | tr '[:upper:]' '[:lower:]'
  else
    echo "unknown"
  fi
}

detect_linux_distro_family() {
  local distro
  distro="$(detect_linux_distro)"
  case "$distro" in
    ubuntu|debian|linuxmint|pop|kali) echo "debian" ;;
    fedora|rhel|centos|rocky|almalinux) echo "rhel" ;;
    arch|manjaro|endeavouros) echo "arch" ;;
    *) echo "unknown" ;;
  esac
}

detect_package_manager() {
  local os
  os="$(detect_os)"
  case "$os" in
    macos)
      command -v brew &>/dev/null && echo "brew" || echo "none"
      ;;
    linux|wsl)
      local family
      family="$(detect_linux_distro_family)"
      case "$family" in
        debian) echo "apt" ;;
        rhel)   echo "dnf" ;;
        arch)   echo "pacman" ;;
        *)
          command -v apt-get &>/dev/null && echo "apt" || \
          command -v dnf &>/dev/null && echo "dnf" || \
          command -v pacman &>/dev/null && echo "pacman" || \
          echo "none"
          ;;
      esac
      ;;
    *) echo "none" ;;
  esac
}

# ── Command / version helpers ─────────────────────────────────────────────────

has_command() {
  command -v "$1" &>/dev/null
}

# Run a command and return its output; return empty string on failure
get_version_output() {
  local cmd="$1"
  shift
  # run in subshell so stderr can be merged if needed
  "$cmd" "$@" 2>&1 || true
}

# Extract version string from output using a regex
# Usage: extract_version <output> <regex>
# Regex must have one capture group matching the version
extract_version() {
  local output="$1"
  local regex="$2"
  local version=""

  # Guard: empty regex causes bash to error
  [[ -z "$regex" ]] && return 0

  if [[ "$output" =~ $regex ]]; then
    version="${BASH_REMATCH[1]}"
  fi
  echo "$version"
}

# Compare two semver-ish version strings
# Returns 0 if actual >= required, 1 otherwise
# Handles: 1.2.3, 1.2, 1 and optional "v" prefix and trailing non-numeric
version_at_least() {
  local actual="$1"
  local required="$2"

  # Strip leading "v" or "V"
  actual="${actual#[vV]}"
  required="${required#[vV]}"

  # Strip anything after a space (e.g. build metadata)
  actual="${actual%% *}"
  required="${required%% *}"

  # Normalize: keep only digits and dots
  actual="$(echo "$actual" | grep -oE '^[0-9]+(\.[0-9]+)*')"
  required="$(echo "$required" | grep -oE '^[0-9]+(\.[0-9]+)*')"

  [[ -z "$actual" ]] && return 1

  # Split into parts
  IFS='.' read -ra actual_parts <<< "$actual"
  IFS='.' read -ra required_parts <<< "$required"

  local max_len="${#actual_parts[@]}"
  [[ "${#required_parts[@]}" -gt "$max_len" ]] && max_len="${#required_parts[@]}"

  for ((i = 0; i < max_len; i++)); do
    local a="${actual_parts[$i]:-0}"
    local r="${required_parts[$i]:-0}"
    if (( a > r )); then return 0; fi
    if (( a < r )); then return 1; fi
  done
  return 0  # equal
}

# ── Print detection info ───────────────────────────────────────────────────────

print_system_info() {
  local os arch pkg shell_name os_version

  os="$(detect_os)"
  arch="$(detect_arch)"
  pkg="$(detect_package_manager)"
  shell_name="$(basename "${SHELL:-unknown}")"

  case "$os" in
    macos)
      os_version="macOS $(sw_vers -productVersion 2>/dev/null || echo '?') (Darwin)"
      ;;
    linux)
      os_version="Linux ($(detect_linux_distro))"
      ;;
    wsl)
      os_version="WSL/Linux ($(detect_linux_distro))"
      ;;
    *)
      os_version="Unknown OS"
      ;;
  esac

  printf '  %-8s %s\n' "OS:" "$os_version"
  printf '  %-8s %s\n' "Arch:" "$arch"
  printf '  %-8s %s\n' "Shell:" "$shell_name"

  if [[ "$pkg" == "brew" ]]; then
    printf '  %-8s %sHomebrew detected%s %s\n' "Pkg:" "${GREEN}" "${RESET}" "✓"
  elif [[ "$pkg" == "none" ]]; then
    printf '  %-8s %sno package manager detected%s\n' "Pkg:" "${YELLOW}" "${RESET}"
  else
    printf '  %-8s %s\n' "Pkg:" "$pkg"
  fi

  if [[ "$os" == "wsl" ]]; then
    printf '\n  %s[WSL detected]%s Docker Desktop handles Docker via WSL integration.\n' "${BOLD_YELLOW}" "${RESET}"
    printf '  Ensure Docker Desktop -> Settings -> Resources -> WSL Integration is enabled.\n'
  fi
}
