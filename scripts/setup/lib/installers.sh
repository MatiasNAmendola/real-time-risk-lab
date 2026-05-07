#!/usr/bin/env bash
# installers.sh — Generic install helpers for brew, apt, dnf, direct

[[ -n "${_INSTALLERS_SH_LOADED:-}" ]] && return 0
_INSTALLERS_SH_LOADED=1

SCRIPT_DIR_INST="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=logging.sh
source "${SCRIPT_DIR_INST}/logging.sh"
# shellcheck source=detect.sh
source "${SCRIPT_DIR_INST}/detect.sh"

# Global dry-run flag (set from main script)
DRY_RUN="${DRY_RUN:-0}"

# ── sudo cache ────────────────────────────────────────────────────────────────

_SUDO_CACHED=0

ensure_sudo() {
  if [[ $_SUDO_CACHED -eq 1 ]]; then return 0; fi
  if [[ "$(id -u)" -eq 0 ]]; then
    _SUDO_CACHED=1; return 0
  fi
  log_info "This step requires sudo access. You will be prompted once."
  if sudo -v 2>/dev/null; then
    _SUDO_CACHED=1
    # Keep sudo alive in background
    (while true; do sudo -v; sleep 50; done) &
    _SUDO_BG_PID=$!
    return 0
  else
    log_error "sudo authentication failed"
    return 1
  fi
}

# Call this at end of install session to kill background sudo refresher
release_sudo() {
  if [[ -n "${_SUDO_BG_PID:-}" ]]; then
    kill "$_SUDO_BG_PID" 2>/dev/null || true
    unset _SUDO_BG_PID
  fi
}

# ── Run with dry-run awareness ─────────────────────────────────────────────────

run_cmd() {
  if [[ "$DRY_RUN" == "1" ]]; then
    log_arrow "[dry-run] $*"
    return 0
  fi
  log_arrow "Running: $*"
  "$@"
}

# ── Retry helper ───────────────────────────────────────────────────────────────

retry() {
  local retries="${1:-3}"
  local delay="${2:-2}"
  shift 2
  local attempt=1

  while (( attempt <= retries )); do
    if "$@"; then return 0; fi
    log_warn "Attempt $attempt/$retries failed. Retrying in ${delay}s..."
    sleep "$delay"
    (( attempt++ ))
    (( delay = delay * 2 ))
  done
  log_error "Command failed after $retries attempts: $*"
  return 1
}

# ── Homebrew ───────────────────────────────────────────────────────────────────

install_homebrew() {
  if has_command brew; then
    log_ok "Homebrew already installed"
    return 0
  fi
  if [[ "$DRY_RUN" == "1" ]]; then
    log_arrow "[dry-run] Would install Homebrew from https://brew.sh"
    return 0
  fi
  log_step "Installing Homebrew..."
  /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
  # Add brew to PATH for Apple Silicon
  if [[ -f /opt/homebrew/bin/brew ]]; then
    eval "$(/opt/homebrew/bin/brew shellenv)"
  elif [[ -f /usr/local/bin/brew ]]; then
    eval "$(/usr/local/bin/brew shellenv)"
  fi
}

install_with_brew() {
  local package="$1"
  local cask="${2:-0}"  # set to "cask" for cask installs

  if ! has_command brew; then
    log_error "Homebrew not found. Cannot install $package"
    return 1
  fi

  local brew_args=()
  if [[ "$cask" == "cask" ]] || [[ "$cask" == "1" ]]; then
    brew_args=(install --cask "$package")
  else
    brew_args=(install "$package")
  fi

  if [[ "$DRY_RUN" == "1" ]]; then
    log_arrow "[dry-run] brew ${brew_args[*]}"
    return 0
  fi

  log_arrow "brew ${brew_args[*]}"
  retry 3 2 brew "${brew_args[@]}"
}

brew_upgrade() {
  local package="$1"
  local cask="${2:-0}"

  local brew_args=()
  if [[ "$cask" == "cask" ]] || [[ "$cask" == "1" ]]; then
    brew_args=(upgrade --cask "$package")
  else
    brew_args=(upgrade "$package")
  fi

  if [[ "$DRY_RUN" == "1" ]]; then
    log_arrow "[dry-run] brew ${brew_args[*]}"
    return 0
  fi

  log_arrow "brew ${brew_args[*]}"
  brew "${brew_args[@]}" 2>/dev/null || brew install "$package" || true
}

# ── apt ────────────────────────────────────────────────────────────────────────

apt_update_done=0

install_with_apt() {
  local package="$1"

  if ! ensure_sudo; then return 1; fi

  if [[ "$DRY_RUN" == "1" ]]; then
    log_arrow "[dry-run] sudo apt-get install -y $package"
    return 0
  fi

  if [[ $apt_update_done -eq 0 ]]; then
    log_arrow "sudo apt-get update"
    sudo apt-get update -qq
    apt_update_done=1
  fi

  log_arrow "sudo apt-get install -y $package"
  retry 3 2 sudo apt-get install -y "$package"
}

# Add an apt repo (PPA or external)
add_apt_repo() {
  local name="$1"
  local key_url="$2"
  local repo_line="$3"
  local list_file="${4:-/etc/apt/sources.list.d/${name}.list}"

  if ! ensure_sudo; then return 1; fi
  if [[ "$DRY_RUN" == "1" ]]; then
    log_arrow "[dry-run] Would add apt repo: $name"
    return 0
  fi

  log_arrow "Adding apt repo: $name"
  if [[ -n "$key_url" ]]; then
    curl -fsSL "$key_url" | sudo gpg --dearmor -o "/etc/apt/keyrings/${name}.gpg" 2>/dev/null || \
    curl -fsSL "$key_url" | sudo apt-key add - 2>/dev/null || true
  fi
  if [[ -n "$repo_line" ]]; then
    echo "$repo_line" | sudo tee "$list_file" > /dev/null
    apt_update_done=0  # force re-update after adding repo
  fi
}

# ── dnf ────────────────────────────────────────────────────────────────────────

install_with_dnf() {
  local package="$1"

  if ! ensure_sudo; then return 1; fi

  if [[ "$DRY_RUN" == "1" ]]; then
    log_arrow "[dry-run] sudo dnf install -y $package"
    return 0
  fi

  log_arrow "sudo dnf install -y $package"
  retry 3 2 sudo dnf install -y "$package"
}

# ── pacman ─────────────────────────────────────────────────────────────────────

install_with_pacman() {
  local package="$1"

  if ! ensure_sudo; then return 1; fi

  if [[ "$DRY_RUN" == "1" ]]; then
    log_arrow "[dry-run] sudo pacman -S --noconfirm $package"
    return 0
  fi

  log_arrow "sudo pacman -S --noconfirm $package"
  retry 3 2 sudo pacman -S --noconfirm "$package"
}

# ── Direct download ────────────────────────────────────────────────────────────

# Download, extract and install a binary from a tarball or zip
# Usage: install_direct <url> <binary_name> [install_dir]
install_direct() {
  local url="$1"
  local binary_name="$2"
  local install_dir="${3:-/usr/local/bin}"

  if [[ "$DRY_RUN" == "1" ]]; then
    log_arrow "[dry-run] Would download and install $binary_name from $url -> $install_dir"
    return 0
  fi

  local tmpdir
  tmpdir="$(mktemp -d)"
  trap 'rm -rf "$tmpdir"' RETURN

  log_arrow "Downloading $binary_name from $url"
  retry 3 2 curl -fsSL --output-dir "$tmpdir" -O "$url" || {
    log_error "Failed to download $url"
    return 1
  }

  local filename
  filename="$(basename "$url")"
  local filepath="$tmpdir/$filename"

  # Extract
  case "$filename" in
    *.tar.gz|*.tgz)
      tar -xzf "$filepath" -C "$tmpdir"
      ;;
    *.tar.xz)
      tar -xJf "$filepath" -C "$tmpdir"
      ;;
    *.zip)
      unzip -q "$filepath" -d "$tmpdir"
      ;;
    *)
      # Assume raw binary
      chmod +x "$filepath"
      if [[ "$install_dir" == /usr/* ]] || [[ "$install_dir" == /opt/* ]]; then
        ensure_sudo || return 1
        sudo cp "$filepath" "$install_dir/$binary_name"
      else
        mkdir -p "$install_dir"
        cp "$filepath" "$install_dir/$binary_name"
      fi
      return 0
      ;;
  esac

  # Find binary in extracted contents
  local found_bin
  found_bin="$(find "$tmpdir" -name "$binary_name" -type f 2>/dev/null | head -1)"
  if [[ -z "$found_bin" ]]; then
    log_error "Binary '$binary_name' not found in archive"
    return 1
  fi

  chmod +x "$found_bin"
  if [[ "$install_dir" == /usr/* ]] || [[ "$install_dir" == /opt/* ]]; then
    ensure_sudo || return 1
    sudo mv "$found_bin" "$install_dir/$binary_name"
  else
    mkdir -p "$install_dir"
    mv "$found_bin" "$install_dir/$binary_name"
  fi

  log_ok "Installed $binary_name to $install_dir/$binary_name"
}

# ── Shell RC patching ──────────────────────────────────────────────────────────

# Append a block to shell RC if not already present
# Usage: append_to_shell_rc <marker> <content>
append_to_shell_rc() {
  local marker="$1"
  local content="$2"
  local rc_file

  # Detect the user's shell RC
  local current_shell
  current_shell="$(basename "${SHELL:-bash}")"
  case "$current_shell" in
    zsh)  rc_file="${HOME}/.zshrc" ;;
    bash) rc_file="${HOME}/.bashrc" ;;
    *)    rc_file="${HOME}/.profile" ;;
  esac

  if grep -qF "$marker" "$rc_file" 2>/dev/null; then
    log_debug "Shell RC already contains: $marker"
    return 0
  fi

  if [[ "$DRY_RUN" == "1" ]]; then
    log_arrow "[dry-run] Would append to $rc_file:"
    log_arrow "  $content"
    return 0
  fi

  printf '\n# %s\n%s\n' "$marker" "$content" >> "$rc_file"
  log_ok "Added to $rc_file: $marker"
}
