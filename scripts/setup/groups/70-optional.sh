#!/usr/bin/env bash
# 70-optional.sh — Obsidian, watch, htop, btop
# Tools: 4 (all optional)

GROUP_NAME="optional"
GROUP_DESCRIPTION="Optional utilities and productivity tools"

SCRIPT_DIR_GROUP="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="${SCRIPT_DIR_GROUP}/../lib"
source "${LIB_DIR}/detect.sh"
source "${LIB_DIR}/logging.sh"
source "${LIB_DIR}/installers.sh"
source "${LIB_DIR}/prompt.sh"

MISSING_TOOLS=()
OUTDATED_TOOLS=()
OPTIONAL_MISSING=()

check_obsidian() {
  local os
  os="$(detect_os)"
  if [[ "$os" == "macos" ]] && [[ -d "/Applications/Obsidian.app" ]]; then
    log_tool_status "obsidian" "installed" "optional_ok"
  elif has_command obsidian; then
    log_tool_status "obsidian" "installed" "optional_ok"
  else
    log_tool_status "obsidian" "" "optional"
    OPTIONAL_MISSING+=("obsidian")
  fi
}

check_watch() {
  if has_command watch; then
    local output version
    output="$(watch --version 2>&1 || true)"
    version="$(extract_version "$output" 'v([0-9]+\.[0-9]+)')"
    log_tool_status "watch" "${version:-installed}" "optional_ok"
  else
    log_tool_status "watch" "" "optional"
    OPTIONAL_MISSING+=("watch")
  fi
}

check_htop() {
  if has_command htop; then
    local output version
    output="$(htop --version 2>&1 || true)"
    version="$(extract_version "$output" 'htop ([0-9]+\.[0-9]+\.[0-9]+)')"
    log_tool_status "htop" "${version:-installed}" "optional_ok"
  else
    log_tool_status "htop" "" "optional"
    OPTIONAL_MISSING+=("htop")
  fi
}

check_btop() {
  if has_command btop; then
    local output version
    output="$(btop --version 2>&1 || true)"
    version="$(extract_version "$output" 'btop version: ([0-9]+\.[0-9]+\.[0-9]+)')"
    log_tool_status "btop" "${version:-installed}" "optional_ok"
  else
    log_tool_status "btop" "" "optional"
    OPTIONAL_MISSING+=("btop")
  fi
}

check_k6() {
  if has_command k6; then
    local output version
    output="$(k6 version 2>&1 || true)"
    version="$(extract_version "$output" 'k6 v([0-9]+\.[0-9]+\.[0-9]+)')"
    log_tool_status "k6" "${version:-installed}" "optional_ok"
  else
    log_tool_status "k6" "" "optional"
    OPTIONAL_MISSING+=("k6")
  fi
}

install_k6() {
  local os pkg_mgr
  os="$(detect_os)"
  pkg_mgr="$(detect_package_manager)"
  log_step "Installing k6..."
  case "$os" in
    macos)
      case "$pkg_mgr" in
        brew) install_with_brew "k6" ;;
        *)    log_info "Install k6 manually: https://k6.io/docs/get-started/installation/" ;;
      esac
      ;;
    linux|wsl)
      case "$pkg_mgr" in
        apt)
          log_info "Add Grafana repo and install k6:"
          log_info "  sudo gpg -k && sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69"
          log_info "  echo \"deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main\" | sudo tee /etc/apt/sources.list.d/k6.list"
          log_info "  sudo apt-get update && sudo apt-get install -y k6"
          ;;
        dnf|yum)
          log_info "Add Grafana repo and install k6:"
          log_info "  sudo dnf install -y https://dl.k6.io/rpm/repo.rpm && sudo dnf install -y k6"
          ;;
        *)
          log_info "Install k6 via curl:"
          log_info "  curl -sL https://github.com/grafana/k6/releases/latest/download/k6-linux-amd64.tar.gz | tar xz"
          log_info "  sudo mv k6-*/k6 /usr/local/bin/"
          ;;
      esac
      ;;
  esac
}

install_obsidian() {
  local os pkg_mgr
  os="$(detect_os)"
  pkg_mgr="$(detect_package_manager)"

  log_step "Installing Obsidian..."
  case "$os" in
    macos)
      case "$pkg_mgr" in
        brew) install_with_brew "obsidian" "cask" ;;
        *)
          log_info "Download Obsidian from: https://obsidian.md/download"
          log_info "macOS: https://github.com/obsidianmd/obsidian-releases/releases/latest"
          ;;
      esac
      ;;
    linux|wsl)
      case "$pkg_mgr" in
        apt)
          local arch
          arch="$(detect_arch)"
          local obs_arch="amd64"
          [[ "$arch" == "arm64" ]] && obs_arch="arm64"
          log_info "Download .deb from: https://github.com/obsidianmd/obsidian-releases/releases/latest"
          log_info "Example: obsidian_X.X.X_${obs_arch}.deb"
          ;;
        *)
          log_info "Download Obsidian from: https://obsidian.md/download"
          ;;
      esac
      ;;
  esac
}

install_watch() {
  local pkg_mgr
  pkg_mgr="$(detect_package_manager)"
  log_step "Installing watch..."
  case "$pkg_mgr" in
    brew) install_with_brew "watch" ;;
    apt)  install_with_apt "procps" ;;
    dnf)  install_with_dnf "procps-ng" ;;
    pacman) install_with_pacman "procps-ng" ;;
    *) log_warn "Install watch manually" ; return 1 ;;
  esac
}

install_htop() {
  local pkg_mgr
  pkg_mgr="$(detect_package_manager)"
  log_step "Installing htop..."
  case "$pkg_mgr" in
    brew)   install_with_brew "htop" ;;
    apt)    install_with_apt "htop" ;;
    dnf)    install_with_dnf "htop" ;;
    pacman) install_with_pacman "htop" ;;
    *) log_warn "Install htop manually" ; return 1 ;;
  esac
}

install_btop() {
  local pkg_mgr
  pkg_mgr="$(detect_package_manager)"
  log_step "Installing btop..."
  case "$pkg_mgr" in
    brew)   install_with_brew "btop" ;;
    apt)    install_with_apt "btop" ;;
    dnf)    install_with_dnf "btop" ;;
    pacman) install_with_pacman "btop" ;;
    *) log_warn "Install btop manually" ; return 1 ;;
  esac
}

group_check() {
  MISSING_TOOLS=()
  OUTDATED_TOOLS=()
  OPTIONAL_MISSING=()
  printf '\n  %s[%s]%s %s\n' "${BOLD}" "$GROUP_NAME" "${RESET}" "$GROUP_DESCRIPTION"
  check_obsidian
  check_watch
  check_htop
  check_btop
  check_k6
}

group_install() {
  local upgrade="${1:-0}"

  [[ ${#OPTIONAL_MISSING[@]} -eq 0 ]] && return 0

  printf '\n  All %s tools are optional.\n' "$GROUP_NAME"

  for tool in "${OPTIONAL_MISSING[@]}"; do
    if confirm "  Install optional tool: $tool?" "n"; then
      case "$tool" in
        obsidian) install_obsidian && record_install "obsidian" || record_failure "obsidian" "install failed" ;;
        watch)    install_watch    && record_install "watch"    || record_failure "watch" "install failed" ;;
        htop)     install_htop     && record_install "htop"     || record_failure "htop" "install failed" ;;
        btop)     install_btop     && record_install "btop"     || record_failure "btop" "install failed" ;;
        k6)       install_k6       && record_install "k6"       || record_failure "k6" "install failed" ;;
      esac
    else
      record_skip "$tool"
    fi
  done
}
