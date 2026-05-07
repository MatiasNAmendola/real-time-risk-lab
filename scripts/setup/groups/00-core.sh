#!/usr/bin/env bash
# 00-core.sh — Core tools: bash 5+, curl, git, jq, yq, make
# Tools: 6

GROUP_NAME="core"
GROUP_DESCRIPTION="Core shell utilities"

SCRIPT_DIR_GROUP="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="${SCRIPT_DIR_GROUP}/../lib"
source "${LIB_DIR}/detect.sh"
source "${LIB_DIR}/logging.sh"
source "${LIB_DIR}/installers.sh"
source "${LIB_DIR}/prompt.sh"

# ── Tool definitions ───────────────────────────────────────────────────────────
# Format: name|version_cmd|version_regex|min_version|brew_pkg|apt_pkg|dnf_pkg|optional(0/1)

declare -a CORE_TOOLS=(
  "bash|bash --version|GNU bash, version ([0-9]+\.[0-9]+\.[0-9]+)|5.0.0|bash|bash|bash|0"
  "curl|curl --version|curl ([0-9]+\.[0-9]+\.[0-9]+)|7.0.0|curl|curl|curl|0"
  "git|git --version|git version ([0-9]+\.[0-9]+\.[0-9]+)|2.30.0|git|git|git|0"
  "jq|jq --version|jq-([0-9]+\.[0-9]+)|1.6.0|jq|jq|jq|0"
  "yq|yq --version|version v([0-9]+\.[0-9]+\.[0-9]+)|4.40.0|yq|skip|skip|0"
  "make|make --version|GNU Make ([0-9]+\.[0-9]+)|3.80|make|make|make|0"
)

check_tool() {
  local spec="$1"
  IFS='|' read -r name version_cmd version_regex min_version brew_pkg apt_pkg dnf_pkg optional <<< "$spec"

  if ! has_command "$name"; then
    log_tool_status "$name" "" "missing"
    echo "missing:$name:$spec"
    return
  fi

  # Special: for bash, check the actual bash we're interested in
  local version_output
  version_output="$(eval "$version_cmd" 2>&1 || true)"
  local version
  version="$(extract_version "$version_output" "$version_regex")"

  if [[ -z "$version" ]]; then
    log_tool_status "$name" "?" "outdated"
    echo "missing:$name:$spec"
    return
  fi

  if version_at_least "$version" "$min_version"; then
    log_tool_status "$name" "$version" "ok"
    echo "ok:$name"
  else
    log_tool_status "$name" "$version (need >=$min_version)" "outdated"
    echo "outdated:$name:$spec"
  fi
}

install_tool() {
  local spec="$1"
  IFS='|' read -r name version_cmd version_regex min_version brew_pkg apt_pkg dnf_pkg optional <<< "$spec"

  local os pkg_mgr
  os="$(detect_os)"
  pkg_mgr="$(detect_package_manager)"

  log_step "Installing $name..."

  # Special handling for yq on Linux (not in apt repos by default)
  if [[ "$name" == "yq" ]] && [[ "$os" != "macos" ]]; then
    local arch
    arch="$(detect_arch)"
    local yq_arch="$arch"
    [[ "$arch" == "amd64" ]] && yq_arch="amd64"
    [[ "$arch" == "arm64" ]] && yq_arch="arm64"
    install_direct \
      "https://github.com/mikefarah/yq/releases/latest/download/yq_linux_${yq_arch}" \
      "yq" \
      "${HOME}/.local/bin"
    return $?
  fi

  case "$pkg_mgr" in
    brew)
      [[ "$brew_pkg" != "skip" ]] && install_with_brew "$brew_pkg"
      ;;
    apt)
      [[ "$apt_pkg" != "skip" ]] && install_with_apt "$apt_pkg"
      ;;
    dnf)
      [[ "$dnf_pkg" != "skip" ]] && install_with_dnf "$dnf_pkg"
      ;;
    pacman)
      install_with_pacman "$name"
      ;;
    *)
      log_error "No supported package manager found for $name"
      return 1
      ;;
  esac
}

group_check() {
  printf '\n  %s[%s]%s %s\n' "${BOLD}" "$GROUP_NAME" "${RESET}" "$GROUP_DESCRIPTION"
  local missing=()
  local outdated=()

  for spec in "${CORE_TOOLS[@]}"; do
    local result
    result="$(check_tool "$spec")"
    case "$result" in
      missing:*) missing+=("$result") ;;
      outdated:*) outdated+=("$result") ;;
    esac
  done

  MISSING_TOOLS=("${missing[@]}")
  OUTDATED_TOOLS=("${outdated[@]}")
}

group_install() {
  local upgrade="${1:-0}"
  local items=("${MISSING_TOOLS[@]}")
  [[ "$upgrade" == "1" ]] && items+=("${OUTDATED_TOOLS[@]}")

  [[ ${#items[@]} -eq 0 ]] && return 0

  local need_install=()
  for item in "${items[@]}"; do
    local spec="${item#*:}"
    spec="${spec#*:}"
    need_install+=("$spec")
  done

  log_step "Will install (${GROUP_NAME}): $(printf '%s ' "${need_install[@]}" | sed 's/|.*/\n/g' | tr '\n' ' ')"

  if ! confirm "Install missing ${GROUP_NAME} tools?"; then
    for item in "${need_install[@]}"; do
      IFS='|' read -r name _ <<< "$item"
      record_skip "$name"
    done
    return 0
  fi

  for spec in "${need_install[@]}"; do
    IFS='|' read -r name _ <<< "$spec"
    if install_tool "$spec"; then
      record_install "$name"
    else
      record_failure "$name" "install failed"
    fi
  done
}
