#!/usr/bin/env bash
# 50-streaming.sh — rpk (Redpanda CLI)
# Tools: 1

GROUP_NAME="streaming"
GROUP_DESCRIPTION="Redpanda / Kafka streaming tools"

SCRIPT_DIR_GROUP="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="${SCRIPT_DIR_GROUP}/../lib"
source "${LIB_DIR}/detect.sh"
source "${LIB_DIR}/logging.sh"
source "${LIB_DIR}/installers.sh"
source "${LIB_DIR}/prompt.sh"

MISSING_TOOLS=()
OUTDATED_TOOLS=()

check_rpk() {
  if ! has_command rpk; then
    log_tool_status "rpk" "" "missing"
    MISSING_TOOLS+=("rpk")
    return
  fi
  local output version
  output="$(rpk version 2>&1 || true)"
  version="$(extract_version "$output" 'v([0-9]+\.[0-9]+\.[0-9]+)')"
  if version_at_least "$version" "24.2.0"; then
    log_tool_status "rpk" "v$version" "ok"
  else
    log_tool_status "rpk" "v$version (need >=24.2)" "outdated"
    OUTDATED_TOOLS+=("rpk")
  fi
}

install_rpk() {
  local os arch pkg_mgr
  os="$(detect_os)"
  arch="$(detect_arch)"
  pkg_mgr="$(detect_package_manager)"

  log_step "Installing rpk (Redpanda CLI)..."

  case "$pkg_mgr" in
    brew)
      install_with_brew "redpanda-data/tap/redpanda" || {
        log_warn "Brew tap failed, trying direct download..."
        _install_rpk_direct "$os" "$arch"
      }
      ;;
    apt)
      if [[ "$DRY_RUN" != "1" ]]; then
        ensure_sudo || return 1
        retry 3 2 curl -1sLf \
          'https://dl.redpanda.com/nzc4ZYQK3WRGd9sy/redpanda/cfg/setup/bash.deb.sh' \
          | sudo bash
      else
        log_arrow "[dry-run] Would add Redpanda apt repo"
      fi
      install_with_apt "redpanda"
      ;;
    dnf)
      if [[ "$DRY_RUN" != "1" ]]; then
        ensure_sudo || return 1
        retry 3 2 curl -1sLf \
          'https://dl.redpanda.com/nzc4ZYQK3WRGd9sy/redpanda/cfg/setup/bash.rpm.sh' \
          | sudo bash
      else
        log_arrow "[dry-run] Would add Redpanda rpm repo"
      fi
      install_with_dnf "redpanda"
      ;;
    *)
      _install_rpk_direct "$os" "$arch"
      ;;
  esac
}

_install_rpk_direct() {
  local os="$1"
  local arch="$2"

  local rpk_os="linux"
  [[ "$os" == "macos" ]] && rpk_os="darwin"

  local rpk_arch="amd64"
  [[ "$arch" == "arm64" ]] && rpk_arch="arm64"

  install_direct \
    "https://github.com/redpanda-data/redpanda/releases/latest/download/rpk-${rpk_os}-${rpk_arch}.zip" \
    "rpk" "${HOME}/.local/bin"

  if ! echo "${PATH}" | grep -q "${HOME}/.local/bin"; then
    if confirm "  Add ~/.local/bin to PATH in your shell RC?" "Y"; then
      append_to_shell_rc "local bin PATH (set by setup.sh)" \
        "export PATH=\"\$HOME/.local/bin:\$PATH\""
    fi
  fi
}

group_check() {
  MISSING_TOOLS=()
  OUTDATED_TOOLS=()
  printf '\n  %s[%s]%s %s\n' "${BOLD}" "$GROUP_NAME" "${RESET}" "$GROUP_DESCRIPTION"
  check_rpk
}

group_install() {
  local upgrade="${1:-0}"
  local to_install=("${MISSING_TOOLS[@]}")
  [[ "$upgrade" == "1" ]] && to_install+=("${OUTDATED_TOOLS[@]}")

  [[ ${#to_install[@]} -eq 0 ]] && return 0

  printf '\n  Missing/outdated in %s: %s\n' "$GROUP_NAME" "${to_install[*]}"
  if ! confirm "Install missing ${GROUP_NAME} tools?"; then
    for t in "${to_install[@]}"; do record_skip "$t"; done
    return 0
  fi

  for tool in "${to_install[@]}"; do
    case "$tool" in
      rpk) install_rpk && record_install "rpk" || record_failure "rpk" "install failed" ;;
    esac
  done
}
