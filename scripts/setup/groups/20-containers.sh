#!/usr/bin/env bash
# 20-containers.sh — Docker / OrbStack / Docker Desktop + compose v2
# Tools: 3 (docker, orb|desktop, compose)

GROUP_NAME="containers"
GROUP_DESCRIPTION="Container runtime (OrbStack/Docker Desktop + Compose v2)"

SCRIPT_DIR_GROUP="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="${SCRIPT_DIR_GROUP}/../lib"
source "${LIB_DIR}/detect.sh"
source "${LIB_DIR}/logging.sh"
source "${LIB_DIR}/installers.sh"
source "${LIB_DIR}/prompt.sh"

MISSING_TOOLS=()
OUTDATED_TOOLS=()

check_docker() {
  if ! has_command docker; then
    log_tool_status "docker" "" "missing"
    MISSING_TOOLS+=("docker")
    return
  fi
  local output version
  output="$(docker version --format '{{.Client.Version}}' 2>/dev/null || docker --version 2>&1 || true)"
  version="$(extract_version "$output" '([0-9]+\.[0-9]+\.[0-9]+)')"
  if version_at_least "$version" "24.0.0"; then
    # Detect backend
    local backend=""
    has_command orb && backend=" (OrbStack engine)"
    log_tool_status "docker" "$version" "ok"
    [[ -n "$backend" ]] && printf '                        %s%s%s\n' "${GRAY}" "$backend" "${RESET}"
  else
    log_tool_status "docker" "$version (need >=24)" "outdated"
    OUTDATED_TOOLS+=("docker")
  fi
}

check_orb() {
  local os
  os="$(detect_os)"
  [[ "$os" != "macos" ]] && return 0  # OrbStack macOS only

  if has_command orb; then
    local output version
    output="$(orb version 2>&1 || true)"
    version="$(extract_version "$output" '([0-9]+\.[0-9]+\.[0-9]+)')"
    log_tool_status "orb" "$version" "ok"
  else
    # Check Docker Desktop as alternative
    if [[ -d "/Applications/Docker.app" ]]; then
      log_tool_status "docker-desktop" "installed" "ok"
    else
      log_tool_status "orb/desktop" "" "missing"
      MISSING_TOOLS+=("container_runtime")
    fi
  fi
}

check_compose() {
  # Prefer 'docker compose' (plugin) over 'docker-compose' (standalone)
  if docker compose version &>/dev/null 2>&1; then
    local output version
    output="$(docker compose version 2>&1 || true)"
    version="$(extract_version "$output" 'v([0-9]+\.[0-9]+\.[0-9]+)')"
    if version_at_least "$version" "2.20.0"; then
      log_tool_status "compose" "v$version" "ok"
    else
      log_tool_status "compose" "v$version (need >=2.20)" "outdated"
      OUTDATED_TOOLS+=("compose")
    fi
  elif has_command docker-compose; then
    local output version
    output="$(docker-compose version 2>&1 || true)"
    version="$(extract_version "$output" '([0-9]+\.[0-9]+\.[0-9]+)')"
    log_tool_status "compose" "$version (v1, prefer v2 plugin)" "outdated"
    OUTDATED_TOOLS+=("compose")
  else
    log_tool_status "compose" "" "missing"
    MISSING_TOOLS+=("compose")
  fi
}

install_container_runtime() {
  local os
  os="$(detect_os)"

  if [[ "$os" == "wsl" ]]; then
    log_warn "WSL detected: Docker should be handled via Docker Desktop Windows integration."
    log_info "Enable: Docker Desktop -> Settings -> Resources -> WSL Integration"
    return 0
  fi

  if [[ "$os" == "macos" ]]; then
    local choice
    choice="$(select_option "Choose container runtime:" \
      "OrbStack (recommended, lightweight)" \
      "Docker Desktop (official)")"

    case "$choice" in
      0)
        log_step "Installing OrbStack..."
        install_with_brew "orbstack" "cask"
        ;;
      1)
        log_step "Installing Docker Desktop..."
        install_with_brew "docker" "cask"
        ;;
    esac
  else
    # Linux: official Docker CE
    local distro_family
    distro_family="$(detect_linux_distro_family)"
    log_step "Installing Docker CE..."
    case "$distro_family" in
      debian)
        if [[ "$DRY_RUN" != "1" ]]; then
          ensure_sudo || return 1
          retry 3 2 curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
            | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
          echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" \
            | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
          apt_update_done=0
        else
          log_arrow "[dry-run] Would add Docker apt repo"
        fi
        install_with_apt "docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin"
        if ! id -nG "$USER" | grep -qw docker; then
          if confirm "  Add $USER to docker group (no sudo for docker)?"; then
            ensure_sudo && sudo usermod -aG docker "$USER"
            log_warn "Log out and back in for group change to take effect"
          fi
        fi
        ;;
      rhel)
        install_with_dnf "docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin"
        ;;
      *)
        log_warn "Docker install not automated for this distro. Visit https://docs.docker.com/engine/install/"
        return 1
        ;;
    esac
  fi
}

install_compose() {
  # Compose v2 is bundled with Docker Desktop/OrbStack and the docker-compose-plugin package
  # If missing as a plugin, install standalone for Linux
  local os pkg_mgr
  os="$(detect_os)"
  pkg_mgr="$(detect_package_manager)"

  log_step "Installing Docker Compose v2..."
  if [[ "$os" == "macos" ]]; then
    log_info "Compose v2 is bundled with OrbStack/Docker Desktop. Reinstall Docker if missing."
  else
    case "$pkg_mgr" in
      apt) install_with_apt "docker-compose-plugin" ;;
      dnf) install_with_dnf "docker-compose-plugin" ;;
      *)
        local arch
        arch="$(detect_arch)"
        [[ "$arch" == "amd64" ]] && arch="x86_64"
        install_direct \
          "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-${arch}" \
          "docker-compose" \
          "${HOME}/.docker/cli-plugins"
        ;;
    esac
  fi
}

group_check() {
  MISSING_TOOLS=()
  OUTDATED_TOOLS=()
  printf '\n  %s[%s]%s %s\n' "${BOLD}" "$GROUP_NAME" "${RESET}" "$GROUP_DESCRIPTION"
  check_docker
  check_orb
  check_compose
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
      docker|container_runtime)
        install_container_runtime && record_install "docker" || record_failure "docker" "install failed"
        ;;
      compose)
        install_compose && record_install "compose" || record_failure "compose" "install failed"
        ;;
    esac
  done
}
