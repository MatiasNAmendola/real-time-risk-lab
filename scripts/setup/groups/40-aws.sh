#!/usr/bin/env bash
# 40-aws.sh — AWS CLI v2 (talks to the Floci AWS emulator on :4566, ADR-0042)
# Tools: 1 required (aws), 1 optional (mc — kept for users still doing ad-hoc MinIO admin)

GROUP_NAME="aws"
GROUP_DESCRIPTION="AWS CLI (Floci client; mc optional)"

SCRIPT_DIR_GROUP="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="${SCRIPT_DIR_GROUP}/../lib"
source "${LIB_DIR}/detect.sh"
source "${LIB_DIR}/logging.sh"
source "${LIB_DIR}/installers.sh"
source "${LIB_DIR}/prompt.sh"

MISSING_TOOLS=()
OUTDATED_TOOLS=()

check_aws() {
  if ! has_command aws; then
    log_tool_status "aws" "" "missing"
    MISSING_TOOLS+=("aws")
    return
  fi
  local output version
  output="$(aws --version 2>&1 || true)"
  version="$(extract_version "$output" 'aws-cli/([0-9]+\.[0-9]+\.[0-9]+)')"
  # Must be v2
  local major="${version%%.*}"
  if [[ "$major" != "2" ]]; then
    log_tool_status "aws" "$version (need v2.x)" "outdated"
    OUTDATED_TOOLS+=("aws")
    return
  fi
  if version_at_least "$version" "2.15.0"; then
    log_tool_status "aws" "$version" "ok"
  else
    log_tool_status "aws" "$version (need >=2.15)" "outdated"
    OUTDATED_TOOLS+=("aws")
  fi
}

check_mc() {
  if ! has_command mc; then
    # mc might conflict with Midnight Commander; also check 'mcli'
    if has_command mcli; then
      local output version
      output="$(mcli --version 2>&1 || true)"
      version="$(extract_version "$output" 'RELEASE\.([0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]+-[0-9]+-[0-9]+Z)')"
      log_tool_status "mc(li)" "$version" "ok"
      return
    fi
    log_tool_status "mc" "" "missing"
    MISSING_TOOLS+=("mc")
    return
  fi
  local output version
  output="$(mc --version 2>&1 || true)"
  # Check if this is MinIO client vs Midnight Commander
  if ! echo "$output" | grep -qi "minio"; then
    log_tool_status "mc" "" "missing"
    log_warn "  'mc' found but appears to be Midnight Commander, not MinIO client"
    MISSING_TOOLS+=("mc")
    return
  fi
  version="$(extract_version "$output" 'RELEASE\.([0-9]{4}-[0-9]{2}-[0-9]{2})')"
  if [[ -n "$version" ]]; then
    log_tool_status "mc" "RELEASE.$version" "ok"
  else
    log_tool_status "mc" "installed" "ok"
  fi
}

install_aws() {
  local os arch pkg_mgr
  os="$(detect_os)"
  arch="$(detect_arch)"
  pkg_mgr="$(detect_package_manager)"

  log_step "Installing AWS CLI v2..."

  case "$os" in
    macos)
      case "$pkg_mgr" in
        brew) install_with_brew "awscli" ;;
        *)
          local aws_arch="AWSCLIV2.pkg"
          [[ "$arch" == "arm64" ]] && aws_arch="AWSCLIV2-arm64.pkg"
          if [[ "$DRY_RUN" != "1" ]]; then
            local tmpdir
            tmpdir="$(mktemp -d)"
            trap 'rm -rf "$tmpdir"' RETURN
            retry 3 2 curl -fsSL \
              "https://awscli.amazonaws.com/${aws_arch}" \
              -o "${tmpdir}/${aws_arch}"
            ensure_sudo || return 1
            sudo installer -pkg "${tmpdir}/${aws_arch}" -target /
          else
            log_arrow "[dry-run] Would download and install AWS CLI pkg for macOS ${arch}"
          fi
          ;;
      esac
      ;;
    linux|wsl)
      local aws_arch="x86_64"
      [[ "$arch" == "arm64" ]] && aws_arch="aarch64"
      if [[ "$DRY_RUN" != "1" ]]; then
        local tmpdir
        tmpdir="$(mktemp -d)"
        trap 'rm -rf "$tmpdir"' RETURN
        retry 3 2 curl -fsSL \
          "https://awscli.amazonaws.com/awscli-exe-linux-${aws_arch}.zip" \
          -o "${tmpdir}/awscliv2.zip"
        unzip -q "${tmpdir}/awscliv2.zip" -d "$tmpdir"
        ensure_sudo || return 1
        sudo "${tmpdir}/aws/install" --update
      else
        log_arrow "[dry-run] Would download awscli-exe-linux-${aws_arch}.zip and install"
      fi
      ;;
    *)
      log_error "AWS CLI installation not supported for OS: $os"
      return 1
      ;;
  esac
}

install_mc() {
  local os arch pkg_mgr
  os="$(detect_os)"
  arch="$(detect_arch)"
  pkg_mgr="$(detect_package_manager)"

  log_step "Installing MinIO client (mc)..."

  # Use 'mcli' as binary name if mc is taken by Midnight Commander
  local mc_binary="mc"
  has_command mc && ! mc --version 2>&1 | grep -qi minio && mc_binary="mcli"

  case "$os" in
    macos)
      case "$pkg_mgr" in
        brew)
          install_with_brew "minio/stable/mc" || install_with_brew "minio-mc"
          ;;
        *)
          local mc_arch="darwin-arm64"
          [[ "$arch" == "amd64" ]] && mc_arch="darwin-amd64"
          install_direct \
            "https://dl.min.io/client/mc/release/${mc_arch}/mc" \
            "$mc_binary" "${HOME}/.local/bin"
          ;;
      esac
      ;;
    linux|wsl)
      local mc_arch="linux-amd64"
      [[ "$arch" == "arm64" ]] && mc_arch="linux-arm64"
      install_direct \
        "https://dl.min.io/client/mc/release/${mc_arch}/mc" \
        "$mc_binary" "${HOME}/.local/bin"
      ;;
    *)
      log_error "mc installation not supported for OS: $os"
      return 1
      ;;
  esac

  # Ensure ~/.local/bin is in PATH
  if [[ "$mc_binary" == "mcli" ]] || ! echo "${PATH}" | grep -q "${HOME}/.local/bin"; then
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
  check_aws
  # mc is no longer part of the AWS mocks stack (replaced by Floci, ADR-0042).
  # Keep the installer available via group_install for users who still want it.
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
      aws) install_aws && record_install "aws" || record_failure "aws" "install failed" ;;
      mc)  install_mc  && record_install "mc"  || record_failure "mc" "install failed" ;;
    esac
  done
}
