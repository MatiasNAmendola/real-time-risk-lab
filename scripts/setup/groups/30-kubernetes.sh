#!/usr/bin/env bash
# 30-kubernetes.sh — kubectl, helm, k3d, argocd (opt), kustomize (opt)
# Tools: 5 (3 required + 2 optional)

GROUP_NAME="kubernetes"
GROUP_DESCRIPTION="Kubernetes toolchain"

SCRIPT_DIR_GROUP="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="${SCRIPT_DIR_GROUP}/../lib"
source "${LIB_DIR}/detect.sh"
source "${LIB_DIR}/logging.sh"
source "${LIB_DIR}/installers.sh"
source "${LIB_DIR}/prompt.sh"

MISSING_TOOLS=()
OUTDATED_TOOLS=()
OPTIONAL_MISSING=()

check_kubectl() {
  if ! has_command kubectl; then
    log_tool_status "kubectl" "" "missing"
    MISSING_TOOLS+=("kubectl")
    return
  fi
  local output version
  output="$(kubectl version --client --output=yaml 2>&1 || kubectl version --client 2>&1 || true)"
  version="$(extract_version "$output" 'gitVersion: v([0-9]+\.[0-9]+\.[0-9]+)')"
  [[ -z "$version" ]] && version="$(extract_version "$output" 'Client Version: v([0-9]+\.[0-9]+\.[0-9]+)')"
  [[ -z "$version" ]] && version="$(extract_version "$(kubectl version --client --short 2>/dev/null || true)" 'Client Version: v([0-9]+\.[0-9]+\.[0-9]+)')"
  if version_at_least "$version" "1.28.0"; then
    log_tool_status "kubectl" "$version" "ok"
  else
    log_tool_status "kubectl" "$version (need >=1.28)" "outdated"
    OUTDATED_TOOLS+=("kubectl")
  fi
}

check_helm() {
  if ! has_command helm; then
    log_tool_status "helm" "" "missing"
    MISSING_TOOLS+=("helm")
    return
  fi
  local output version
  output="$(helm version --short 2>&1 || true)"
  version="$(extract_version "$output" 'v([0-9]+\.[0-9]+\.[0-9]+)')"
  if version_at_least "$version" "3.13.0"; then
    log_tool_status "helm" "v$version" "ok"
  else
    log_tool_status "helm" "v$version (need >=3.13)" "outdated"
    OUTDATED_TOOLS+=("helm")
  fi
}

check_k3d() {
  # Check if OrbStack k8s is available as alternative
  local orb_k8s=0
  if has_command orb; then
    local orb_k8s_status
    orb_k8s_status="$(orb config get k8s.enabled 2>/dev/null || true)"
    [[ "$orb_k8s_status" == "true" ]] && orb_k8s=1
  fi

  if has_command k3d; then
    local output version
    output="$(k3d version 2>&1 || true)"
    version="$(extract_version "$output" 'k3d version v([0-9]+\.[0-9]+\.[0-9]+)')"
    if version_at_least "$version" "5.6.0"; then
      log_tool_status "k3d" "v$version" "ok"
    else
      log_tool_status "k3d" "v$version (need >=5.6)" "outdated"
      OUTDATED_TOOLS+=("k3d")
    fi
  elif [[ $orb_k8s -eq 1 ]]; then
    log_tool_status "k3d" "OrbStack k8s active (alt)" "ok"
  else
    log_tool_status "k3d" "" "missing"
    MISSING_TOOLS+=("k3d")
  fi
}

check_argocd() {
  if has_command argocd; then
    local output version
    output="$(argocd version --client 2>&1 || true)"
    version="$(extract_version "$output" 'argocd: v([0-9]+\.[0-9]+\.[0-9]+)')"
    log_tool_status "argocd" "v$version" "optional_ok"
  else
    log_tool_status "argocd" "" "optional"
    OPTIONAL_MISSING+=("argocd")
  fi
}

check_kustomize() {
  if has_command kustomize; then
    local output version
    output="$(kustomize version 2>&1 || true)"
    version="$(extract_version "$output" 'v([0-9]+\.[0-9]+\.[0-9]+)')"
    log_tool_status "kustomize" "v$version" "optional_ok"
  else
    log_tool_status "kustomize" "" "optional"
    OPTIONAL_MISSING+=("kustomize")
  fi
}

install_kubectl() {
  local os pkg_mgr arch
  os="$(detect_os)"
  pkg_mgr="$(detect_package_manager)"
  arch="$(detect_arch)"

  log_step "Installing kubectl..."
  case "$pkg_mgr" in
    brew) install_with_brew "kubernetes-cli" ;;
    apt)
      if [[ "$DRY_RUN" != "1" ]]; then
        ensure_sudo || return 1
        retry 3 2 curl -fsSL https://pkgs.k8s.io/core:/stable:/v1.32/deb/Release.key \
          | sudo gpg --dearmor -o /etc/apt/keyrings/kubernetes-apt-keyring.gpg
        echo "deb [signed-by=/etc/apt/keyrings/kubernetes-apt-keyring.gpg] https://pkgs.k8s.io/core:/stable:/v1.32/deb/ /" \
          | sudo tee /etc/apt/sources.list.d/kubernetes.list > /dev/null
        apt_update_done=0
      else
        log_arrow "[dry-run] Would add Kubernetes apt repo"
      fi
      install_with_apt "kubectl"
      ;;
    dnf)
      if [[ "$DRY_RUN" != "1" ]]; then
        ensure_sudo || return 1
        cat <<EOF | sudo tee /etc/yum.repos.d/kubernetes.repo > /dev/null
[kubernetes]
name=Kubernetes
baseurl=https://pkgs.k8s.io/core:/stable:/v1.32/rpm/
enabled=1
gpgcheck=1
gpgkey=https://pkgs.k8s.io/core:/stable:/v1.32/rpm/repodata/repomd.xml.key
EOF
      else
        log_arrow "[dry-run] Would add Kubernetes dnf repo"
      fi
      install_with_dnf "kubectl"
      ;;
    *)
      local k8s_arch="$arch"
      [[ "$arch" == "amd64" ]] && k8s_arch="amd64"
      [[ "$arch" == "arm64" ]] && k8s_arch="arm64"
      local k8s_os="linux"
      [[ "$os" == "macos" ]] && k8s_os="darwin"
      install_direct \
        "https://dl.k8s.io/release/v1.32.0/bin/${k8s_os}/${k8s_arch}/kubectl" \
        "kubectl" "${HOME}/.local/bin"
      ;;
  esac
}

install_helm() {
  local pkg_mgr
  pkg_mgr="$(detect_package_manager)"

  log_step "Installing Helm..."
  case "$pkg_mgr" in
    brew) install_with_brew "helm" ;;
    apt|dnf)
      if [[ "$DRY_RUN" != "1" ]]; then
        retry 3 2 curl -fsSL https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
      else
        log_arrow "[dry-run] Would run helm install script"
      fi
      ;;
    *)
      if [[ "$DRY_RUN" != "1" ]]; then
        retry 3 2 curl -fsSL https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
      else
        log_arrow "[dry-run] Would run helm install script"
      fi
      ;;
  esac
}

install_k3d() {
  # Check if OrbStack is available as alternative
  if has_command orb; then
    local orb_k8s_status
    orb_k8s_status="$(orb config get k8s.enabled 2>/dev/null || true)"
    if [[ "$orb_k8s_status" != "true" ]]; then
      if confirm "  OrbStack is installed. Enable OrbStack built-in Kubernetes instead of k3d?" "Y"; then
        log_step "Enabling OrbStack Kubernetes..."
        if [[ "$DRY_RUN" != "1" ]]; then
          orb config set k8s.enabled true
          log_ok "OrbStack k8s enabled. Use 'kubectl config use-context orbstack' to switch."
        else
          log_arrow "[dry-run] orb config set k8s.enabled true"
        fi
        return 0
      fi
    fi
  fi

  local pkg_mgr
  pkg_mgr="$(detect_package_manager)"
  log_step "Installing k3d..."
  case "$pkg_mgr" in
    brew) install_with_brew "k3d" ;;
    *)
      if [[ "$DRY_RUN" != "1" ]]; then
        retry 3 2 curl -fsSL https://raw.githubusercontent.com/k3d-io/k3d/main/install.sh | bash
      else
        log_arrow "[dry-run] curl https://raw.githubusercontent.com/k3d-io/k3d/main/install.sh | bash"
      fi
      ;;
  esac
}

install_argocd() {
  local os pkg_mgr arch
  os="$(detect_os)"
  pkg_mgr="$(detect_package_manager)"
  arch="$(detect_arch)"

  log_step "Installing ArgoCD CLI (optional)..."
  case "$pkg_mgr" in
    brew) install_with_brew "argocd" ;;
    *)
      local argocd_os="linux"
      [[ "$os" == "macos" ]] && argocd_os="darwin"
      install_direct \
        "https://github.com/argoproj/argo-cd/releases/latest/download/argocd-${argocd_os}-${arch}" \
        "argocd" "${HOME}/.local/bin"
      ;;
  esac
}

install_kustomize() {
  local pkg_mgr
  pkg_mgr="$(detect_package_manager)"
  log_step "Installing kustomize (optional)..."
  case "$pkg_mgr" in
    brew) install_with_brew "kustomize" ;;
    *)
      if [[ "$DRY_RUN" != "1" ]]; then
        retry 3 2 curl -fsSL "https://raw.githubusercontent.com/kubernetes-sigs/kustomize/master/hack/install_kustomize.sh" \
          | bash -s -- "${HOME}/.local/bin"
      else
        log_arrow "[dry-run] Would install kustomize via install script"
      fi
      ;;
  esac
}

group_check() {
  MISSING_TOOLS=()
  OUTDATED_TOOLS=()
  OPTIONAL_MISSING=()
  printf '\n  %s[%s]%s %s\n' "${BOLD}" "$GROUP_NAME" "${RESET}" "$GROUP_DESCRIPTION"
  check_kubectl
  check_helm
  check_k3d
  check_argocd
  check_kustomize
}

group_install() {
  local upgrade="${1:-0}"
  local to_install=("${MISSING_TOOLS[@]}")
  [[ "$upgrade" == "1" ]] && to_install+=("${OUTDATED_TOOLS[@]}")

  if [[ ${#to_install[@]} -gt 0 ]]; then
    printf '\n  Missing/outdated in %s: %s\n' "$GROUP_NAME" "${to_install[*]}"
    if confirm "Install missing ${GROUP_NAME} tools?"; then
      for tool in "${to_install[@]}"; do
        case "$tool" in
          kubectl)   install_kubectl   && record_install "kubectl"   || record_failure "kubectl" "install failed" ;;
          helm)      install_helm      && record_install "helm"      || record_failure "helm" "install failed" ;;
          k3d)       install_k3d       && record_install "k3d"       || record_failure "k3d" "install failed" ;;
        esac
      done
    else
      for t in "${to_install[@]}"; do record_skip "$t"; done
    fi
  fi

  # Optionals
  if [[ ${#OPTIONAL_MISSING[@]} -gt 0 ]]; then
    for tool in "${OPTIONAL_MISSING[@]}"; do
      if confirm "  Install optional tool: $tool?"; then
        case "$tool" in
          argocd)    install_argocd    && record_install "argocd"    || record_failure "argocd" "install failed" ;;
          kustomize) install_kustomize && record_install "kustomize" || record_failure "kustomize" "install failed" ;;
        esac
      else
        record_skip "$tool"
      fi
    done
  fi
}
