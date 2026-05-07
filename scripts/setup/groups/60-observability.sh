#!/usr/bin/env bash
# 60-observability.sh — otel-cli (opt), websocat, wscat
# Tools: 3 (1 optional + 2 pick-one)

GROUP_NAME="observability"
GROUP_DESCRIPTION="Observability and WebSocket testing tools"

SCRIPT_DIR_GROUP="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="${SCRIPT_DIR_GROUP}/../lib"
source "${LIB_DIR}/detect.sh"
source "${LIB_DIR}/logging.sh"
source "${LIB_DIR}/installers.sh"
source "${LIB_DIR}/prompt.sh"

MISSING_TOOLS=()
OUTDATED_TOOLS=()
OPTIONAL_MISSING=()

check_otel_cli() {
  if has_command otel-cli; then
    local output version
    output="$(otel-cli version 2>&1 || true)"
    version="$(extract_version "$output" 'v([0-9]+\.[0-9]+\.[0-9]+)')"
    log_tool_status "otel-cli" "v${version:-installed}" "optional_ok"
  else
    log_tool_status "otel-cli" "" "optional"
    OPTIONAL_MISSING+=("otel-cli")
  fi
}

check_websocat() {
  if has_command websocat; then
    local output version
    output="$(websocat --version 2>&1 || true)"
    version="$(extract_version "$output" 'websocat ([0-9]+\.[0-9]+\.[0-9]+)')"
    log_tool_status "websocat" "$version" "ok"
  else
    log_tool_status "websocat" "" "missing"
    MISSING_TOOLS+=("websocat")
  fi
}

check_wscat() {
  if has_command wscat; then
    local output version
    output="$(wscat --version 2>&1 || true)"
    version="$(extract_version "$output" '([0-9]+\.[0-9]+\.[0-9]+)')"
    log_tool_status "wscat" "$version" "ok"
  else
    log_tool_status "wscat" "" "missing"
    MISSING_TOOLS+=("wscat")
  fi
}

install_otel_cli() {
  local os arch pkg_mgr
  os="$(detect_os)"
  arch="$(detect_arch)"
  pkg_mgr="$(detect_package_manager)"

  log_step "Installing otel-cli (optional)..."

  case "$pkg_mgr" in
    brew) install_with_brew "equinix-labs/otel-cli/otel-cli" ;;
    *)
      local otel_os="linux"
      [[ "$os" == "macos" ]] && otel_os="darwin"
      local otel_arch="x86_64"
      [[ "$arch" == "arm64" ]] && otel_arch="arm64"
      install_direct \
        "https://github.com/equinix-labs/otel-cli/releases/latest/download/otel-cli_${otel_os}_${otel_arch}.tar.gz" \
        "otel-cli" "${HOME}/.local/bin"
      ;;
  esac
}

install_websocat() {
  local os arch pkg_mgr
  os="$(detect_os)"
  arch="$(detect_arch)"
  pkg_mgr="$(detect_package_manager)"

  log_step "Installing websocat..."

  case "$pkg_mgr" in
    brew) install_with_brew "websocat" ;;
    *)
      local ws_os="linux"
      [[ "$os" == "macos" ]] && ws_os="mac"
      local ws_arch="x86_64"
      [[ "$arch" == "arm64" ]] && ws_arch="aarch64"
      install_direct \
        "https://github.com/vi/websocat/releases/latest/download/websocat.${ws_arch}-unknown-${ws_os}-musl" \
        "websocat" "${HOME}/.local/bin"
      ;;
  esac
}

install_wscat() {
  log_step "Installing wscat (WebSocket CLI, via npm)..."
  if ! has_command npm; then
    log_warn "npm not found. wscat requires Node.js/npm. Install Node.js first: https://nodejs.org"
    return 1
  fi
  if [[ "$DRY_RUN" != "1" ]]; then
    retry 3 2 npm install -g wscat
  else
    log_arrow "[dry-run] npm install -g wscat"
  fi
}

group_check() {
  MISSING_TOOLS=()
  OUTDATED_TOOLS=()
  OPTIONAL_MISSING=()
  printf '\n  %s[%s]%s %s\n' "${BOLD}" "$GROUP_NAME" "${RESET}" "$GROUP_DESCRIPTION"
  check_otel_cli
  check_websocat
  check_wscat
}

group_install() {
  local upgrade="${1:-0}"
  local to_install=("${MISSING_TOOLS[@]}")
  [[ "$upgrade" == "1" ]] && to_install+=("${OUTDATED_TOOLS[@]}")

  if [[ ${#to_install[@]} -gt 0 ]]; then
    printf '\n  Missing/outdated in %s: %s\n' "$GROUP_NAME" "${to_install[*]}"
    if confirm "Install missing ${GROUP_NAME} tools?"; then
      # For websocat/wscat: both do WS testing, at least one needed
      local has_websocat=0
      has_command websocat && has_websocat=1

      for tool in "${to_install[@]}"; do
        case "$tool" in
          websocat)
            if [[ $has_websocat -eq 0 ]]; then
              install_websocat && record_install "websocat" || record_failure "websocat" "install failed"
            fi
            ;;
          wscat)
            # Only install wscat if websocat is also missing
            if ! has_command websocat && ! has_command wscat; then
              install_wscat && record_install "wscat" || record_failure "wscat" "install failed"
            else
              record_skip "wscat"
            fi
            ;;
        esac
      done
    else
      for t in "${to_install[@]}"; do record_skip "$t"; done
    fi
  fi

  # Optionals
  for tool in "${OPTIONAL_MISSING[@]}"; do
    if confirm "  Install optional tool: $tool?" "n"; then
      case "$tool" in
        otel-cli) install_otel_cli && record_install "otel-cli" || record_failure "otel-cli" "install failed" ;;
      esac
    else
      record_skip "$tool"
    fi
  done
}
