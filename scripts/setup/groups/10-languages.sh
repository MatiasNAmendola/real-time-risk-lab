#!/usr/bin/env bash
# 10-languages.sh — Java 21+ runtime/toolchain, Go 1.26+ (goenv preferred), Python 3.11+
# Tools: 3

GROUP_NAME="languages"
GROUP_DESCRIPTION="Programming language runtimes"

SCRIPT_DIR_GROUP="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="${SCRIPT_DIR_GROUP}/../lib"
source "${LIB_DIR}/detect.sh"
source "${LIB_DIR}/logging.sh"
source "${LIB_DIR}/installers.sh"
source "${LIB_DIR}/prompt.sh"

MISSING_TOOLS=()
OUTDATED_TOOLS=()

# ── Version detection helpers ──────────────────────────────────────────────────

get_java_version() {
  local output
  output="$(java -version 2>&1 || true)"
  extract_version "$output" 'version "([0-9]+)'
  # For Java 9+ the format is: openjdk version "25.0.1" or just "25"
  if [[ "$output" =~ version[[:space:]]+"([0-9]+)(\.[0-9]+)?" ]]; then
    echo "${BASH_REMATCH[1]}"
  fi
}

get_java_version_full() {
  local output
  output="$(java -version 2>&1 || true)"
  if [[ "$output" =~ version[[:space:]]+"([0-9]+\.[0-9]+\.[0-9]+)" ]]; then
    echo "${BASH_REMATCH[1]}"
  elif [[ "$output" =~ version[[:space:]]+"([0-9]+)" ]]; then
    echo "${BASH_REMATCH[1]}.0.0"
  fi
}

# ── Check functions ────────────────────────────────────────────────────────────

check_java() {
  if ! has_command java && ! [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
    log_tool_status "java" "" "missing"
    MISSING_TOOLS+=("java")
    return
  fi
  # Resolve the java binary: prefer JAVA_HOME/bin/java, fall back to PATH
  local java_bin
  if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
    java_bin="${JAVA_HOME}/bin/java"
  else
    java_bin="java"
  fi
  local version
  version="$("$java_bin" -version 2>&1 || true)"
  if [[ "$version" =~ version[[:space:]]+"([0-9]+\.[0-9]+\.[0-9]+)" ]]; then
    version="${BASH_REMATCH[1]}"
  elif [[ "$version" =~ version[[:space:]]+"([0-9]+)" ]]; then
    version="${BASH_REMATCH[1]}.0.0"
  fi
  if version_at_least "$version" "21.0.0"; then
    # Detect distribution
    local dist=""
    "$java_bin" -version 2>&1 | grep -qi temurin && dist=" (Temurin)"
    "$java_bin" -version 2>&1 | grep -qi graalvm && dist=" (GraalVM)"
    "$java_bin" -version 2>&1 | grep -qi corretto && dist=" (Corretto)"
    log_tool_status "java" "${version}${dist}" "ok"
  else
    log_tool_status "java" "$version (need >=21)" "outdated"
    OUTDATED_TOOLS+=("java")
  fi
}

check_go() {
  # Prefer goenv-managed Go; fall back to system go
  local go_cmd="go"
  if has_command goenv && goenv version-name &>/dev/null; then
    local goenv_go
    goenv_go="$(goenv which go 2>/dev/null || true)"
    [[ -n "$goenv_go" ]] && go_cmd="$goenv_go"
  fi

  if ! has_command go && [[ "$go_cmd" == "go" ]]; then
    # Also check if goenv is present even if no version is set yet
    if has_command goenv; then
      log_tool_status "go" "goenv present, no version set" "missing"
    else
      log_tool_status "go" "" "missing"
    fi
    MISSING_TOOLS+=("go")
    return
  fi

  local output version
  output="$($go_cmd version 2>&1 || true)"
  version="$(extract_version "$output" 'go([0-9]+\.[0-9]+\.[0-9]+)')"
  if version_at_least "$version" "1.26.0"; then
    log_tool_status "go" "$version" "ok"
  else
    log_tool_status "go" "$version (need >=1.26)" "outdated"
    OUTDATED_TOOLS+=("go")
  fi
}

check_node() {
  # Node.js is optional (only needed for the TypeScript SDK). Preferred manager
  # is fnm (fast, written in Rust, supports `.node-version` auto-switching).
  # Accept any of: fnm with an LTS installed, or system node+npm via another
  # manager (nvm, asdf, Volta, brew install node).
  if has_command fnm; then
    local node_ver=""
    node_ver="$(eval "$(fnm env --shell bash 2>/dev/null || fnm env 2>/dev/null)"; node --version 2>/dev/null || true)"
    if [[ -n "$node_ver" ]]; then
      log_tool_status "node (via fnm)" "$node_ver" "ok"
    else
      log_tool_status "node (via fnm)" "fnm installed, no LTS yet" "missing"
      MISSING_TOOLS+=("node")
    fi
  elif has_command node && has_command npm; then
    log_tool_status "node" "$(node --version 2>/dev/null) (consider fnm)" "ok"
  else
    log_tool_status "node" "" "missing"
    MISSING_TOOLS+=("node")
  fi
}

install_node() {
  local pkg_mgr
  pkg_mgr="$(detect_package_manager)"
  log_step "Installing fnm (Fast Node Manager) + Node.js LTS..."
  case "$pkg_mgr" in
    brew)
      install_with_brew "fnm" || return 1
      ;;
    apt|dnf)
      if [[ "$DRY_RUN" != "1" ]]; then
        retry 3 2 curl -fsSL https://fnm.vercel.app/install | bash -s -- --skip-shell || return 1
      else
        log_arrow "[dry-run] Would run: curl -fsSL https://fnm.vercel.app/install | bash"
      fi
      ;;
    *)
      log_warn "Install fnm manually: https://github.com/Schniz/fnm#installation"
      return 1
      ;;
  esac

  if [[ "$DRY_RUN" != "1" ]] && has_command fnm; then
    eval "$(fnm env --shell bash 2>/dev/null || fnm env 2>/dev/null)"
    fnm install --lts || true
    fnm default lts-latest 2>/dev/null || true
    log_ok "fnm: Node LTS installed and set as default"
    if confirm "  Add fnm activation (eval \"\$(fnm env --use-on-cd)\") to your shell RC?" "Y"; then
      append_to_shell_rc "fnm (set by setup.sh)" \
        'eval "$(fnm env --use-on-cd)"'
    fi
  fi
}

check_python() {
  local python_cmd=""
  has_command python3 && python_cmd="python3"
  has_command python && python3 --version 2>&1 | grep -q "Python 3" && python_cmd="python"

  if [[ -z "$python_cmd" ]]; then
    log_tool_status "python3" "" "missing"
    MISSING_TOOLS+=("python3")
    return
  fi

  local output version
  output="$($python_cmd --version 2>&1 || true)"
  version="$(extract_version "$output" 'Python ([0-9]+\.[0-9]+\.[0-9]+)')"
  if version_at_least "$version" "3.11.0"; then
    log_tool_status "python3" "$version" "ok"
  else
    log_tool_status "python3" "$version (need >=3.11)" "outdated"
    OUTDATED_TOOLS+=("python3")
  fi
}

# ── Install functions ──────────────────────────────────────────────────────────

install_java() {
  local os pkg_mgr
  os="$(detect_os)"
  pkg_mgr="$(detect_package_manager)"

  log_step "Installing Java 21 (Temurin)..."
  case "$pkg_mgr" in
    brew)
      # temurin@21 via Homebrew Cask (adoptium tap)
      brew tap homebrew/cask-versions 2>/dev/null || true
      install_with_brew "temurin@21" "cask" || install_with_brew "openjdk@21"
      ;;
    apt)
      # Eclipse Temurin via Adoptium repo
      if [[ "$DRY_RUN" != "1" ]]; then
        ensure_sudo || return 1
        retry 3 2 curl -fsSL "https://packages.adoptium.net/artifactory/api/gpg/key/public" \
          | sudo gpg --dearmor -o /etc/apt/keyrings/adoptium.gpg
        echo "deb [signed-by=/etc/apt/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb $(lsb_release -cs) main" \
          | sudo tee /etc/apt/sources.list.d/adoptium.list > /dev/null
        apt_update_done=0
      fi
      install_with_apt "temurin-21-jdk"
      ;;
    dnf)
      install_with_dnf "java-21-amazon-corretto-devel" || install_with_dnf "java-latest-openjdk-devel"
      ;;
    *)
      log_warn "Direct Java install not supported for this platform. Visit https://adoptium.net"
      return 1
      ;;
  esac

  # Offer to set JAVA_HOME
  local java_home=""
  if has_command java_home 2>/dev/null; then
    java_home="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
  fi
  [[ -z "$java_home" ]] && java_home="$(dirname "$(dirname "$(readlink -f "$(which java)" 2>/dev/null || true)")" 2>/dev/null || true)"

  if [[ -n "$java_home" ]] && [[ -d "$java_home" ]]; then
    if confirm "  Set JAVA_HOME=$java_home in your shell RC?" "Y"; then
      append_to_shell_rc "JAVA_HOME (set by setup.sh)" \
        "export JAVA_HOME=\"${java_home}\"\nexport PATH=\"\$JAVA_HOME/bin:\$PATH\""
    fi
  fi
}

GO_TARGET_VERSION="1.26.2"

install_go() {
  local os pkg_mgr arch
  os="$(detect_os)"
  pkg_mgr="$(detect_package_manager)"
  arch="$(detect_arch)"

  # NOTE: goenv is the preferred method for developers who maintain multiple Go versions
  # (e.g. Go 1.21 for legacy services + 1.26 for new projects). brew install go replaces
  # the global version; goenv allows per-project swap via .go-version files.
  # If goenv is available, use it. Otherwise fall back to brew/apt/dnf.

  if has_command goenv; then
    log_step "goenv detected — installing Go ${GO_TARGET_VERSION} via goenv..."
    if [[ "$DRY_RUN" != "1" ]]; then
      goenv install "${GO_TARGET_VERSION}" || true
      goenv global "${GO_TARGET_VERSION}"
      log_ok "goenv: Go ${GO_TARGET_VERSION} set as global"
    else
      log_arrow "[dry-run] Would run: goenv install ${GO_TARGET_VERSION} && goenv global ${GO_TARGET_VERSION}"
    fi
  else
    log_step "Installing Go ${GO_TARGET_VERSION}+ (goenv not found, using package manager)..."
    log_arrow "Tip: install goenv first (brew install goenv) for per-project version management"
    case "$pkg_mgr" in
      brew) install_with_brew "go" ;;
      apt)
        local go_os="linux"
        local go_arch="$arch"
        [[ "$arch" == "amd64" ]] && go_arch="amd64"
        [[ "$arch" == "arm64" ]] && go_arch="arm64"
        local go_url="https://go.dev/dl/go${GO_TARGET_VERSION}.${go_os}-${go_arch}.tar.gz"
        if [[ "$DRY_RUN" != "1" ]]; then
          ensure_sudo || return 1
          install_direct "$go_url" "go"
        else
          log_arrow "[dry-run] Would download Go from $go_url"
        fi
        ;;
      dnf)  install_with_dnf "golang" ;;
      *)    log_warn "Install Go manually: https://go.dev/dl/" ; return 1 ;;
    esac
  fi

  # Add GOPATH/bin to PATH if needed
  if ! echo "${PATH}" | grep -q "${HOME}/go/bin"; then
    if confirm "  Add ~/go/bin to PATH in your shell RC?" "Y"; then
      append_to_shell_rc "Go PATH (set by setup.sh)" \
        "export PATH=\"\$HOME/go/bin:\$PATH\""
    fi
  fi

  # Wire goenv shims into PATH if goenv was just used
  if has_command goenv; then
    if ! echo "${PATH}" | grep -q "goenv/shims"; then
      if confirm "  Add goenv shims to PATH in your shell RC?" "Y"; then
        append_to_shell_rc "goenv shims (set by setup.sh)" \
          'export GOENV_ROOT="$HOME/.goenv"\nexport PATH="$GOENV_ROOT/bin:$GOENV_ROOT/shims:$PATH"\neval "$(goenv init -)"'
      fi
    fi
  fi
}

install_python() {
  local pkg_mgr
  pkg_mgr="$(detect_package_manager)"
  log_step "Installing Python 3.11+..."
  case "$pkg_mgr" in
    brew) install_with_brew "python@3.13" ;;
    apt)
      add_apt_repo "deadsnakes" \
        "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0xF23C5A6CF475977595C89F51BA6932366A755776" \
        "deb https://ppa.launchpadcontent.net/deadsnakes/ppa/ubuntu $(lsb_release -cs 2>/dev/null || echo focal) main" \
        "/etc/apt/sources.list.d/deadsnakes.list"
      install_with_apt "python3.13"
      install_with_apt "python3.13-venv"
      ;;
    dnf)  install_with_dnf "python3.13" || install_with_dnf "python3" ;;
    *)    log_warn "Install Python manually: https://python.org/downloads/" ; return 1 ;;
  esac
}

# ── Group interface ────────────────────────────────────────────────────────────

group_check() {
  MISSING_TOOLS=()
  OUTDATED_TOOLS=()
  printf '\n  %s[%s]%s %s\n' "${BOLD}" "$GROUP_NAME" "${RESET}" "$GROUP_DESCRIPTION"
  check_java
  check_go
  check_python
  check_node
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
      java)    install_java    && record_install "java"    || record_failure "java" "install failed" ;;
      go)      install_go      && record_install "go"      || record_failure "go" "install failed" ;;
      python3) install_python  && record_install "python3" || record_failure "python3" "install failed" ;;
      node)    install_node    && record_install "node"    || record_failure "node" "install failed" ;;
    esac
  done
}
