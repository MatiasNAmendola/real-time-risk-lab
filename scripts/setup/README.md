# Real-Time Risk Lab — Setup

Intelligent, idempotent cross-platform installer for the full project toolchain.
Detects what is already installed, installs only what is missing, validates versions,
and never makes changes without asking.

---

## What It Does

- Detects your OS (macOS / Linux / WSL) and architecture (amd64 / arm64)
- Checks each tool against the minimum required version
- Shows a clear status per group before doing anything
- Installs missing tools using the native package manager (Homebrew on macOS, apt/dnf/pacman on Linux)
- Falls back to direct binary downloads when no package manager supports a tool
- Patches your shell RC (`~/.zshrc` or `~/.bashrc`) only when necessary and only with confirmation

---

## Prerequisites

| Platform  | Requirement                                              |
|-----------|----------------------------------------------------------|
| macOS     | Homebrew (will offer to install if missing)              |
| Linux     | `sudo` access; apt / dnf / pacman depending on distro   |
| WSL       | Docker Desktop on Windows with WSL integration enabled   |
| Windows   | Not supported natively — use WSL2                        |

---

## How to Run

```bash
# From repo root
./setup.sh                          # interactive — installs missing tools, confirms per group
./setup.sh --yes                    # non-interactive, auto-confirm all
./setup.sh --dry-run                # preview without making changes
./setup.sh --only core,languages    # install only specified groups
./setup.sh --skip optional          # install all except optional
./setup.sh --upgrade                # also upgrade tools below minimum version
./setup.sh --verify                 # check status only, no installs (exit 1 if anything missing)
./setup.sh --help                   # full usage
```

---

## Tools and Minimum Versions

| Group         | Tool            | Min Version       | Notes                              |
|---------------|-----------------|-------------------|------------------------------------|
| core          | bash            | 5.0.0             | macOS ships 3.2 — upgrade via brew |
| core          | curl            | 7.0.0             |                                    |
| core          | git             | 2.30.0            |                                    |
| core          | jq              | 1.6.0             |                                    |
| core          | yq              | 4.40.0            | mikefarah's yq                     |
| core          | make            | 3.80              |                                    |
| languages     | java            | 25.0.0            | Temurin LTS preferred              |
| languages     | gradle           | 3.9.0             |                                    |
| languages     | go              | 1.26.0            | For Bubble Tea TUI; goenv preferred |
| languages     | python3         | 3.11.0            | For auxiliary scripts              |
| containers    | docker          | 24.0.0            | OrbStack or Docker Desktop         |
| containers    | compose         | 2.20.0            | Plugin v2 (`docker compose`)       |
| kubernetes    | kubectl         | 1.28.0            |                                    |
| kubernetes    | helm            | 3.13.0            |                                    |
| kubernetes    | k3d             | 5.6.0             | OrbStack k8s offered as alt        |
| kubernetes    | argocd          | 2.0.0             | Optional                           |
| kubernetes    | kustomize       | 4.0.0             | Optional                           |
| aws           | aws             | 2.15.0            | CLI v2                             |
| aws           | mc              | RELEASE.2024-*    | MinIO client                       |
| streaming     | rpk             | 24.2.0            | Redpanda CLI                       |
| observability | otel-cli        | 0.0.1             | Optional                           |
| observability | websocat        | 1.0.0             | WebSocket testing                  |
| optional      | obsidian        | any               | Link to download shown if missing  |
| optional      | watch           | any               |                                    |
| optional      | htop            | any               |                                    |
| optional      | btop            | any               |                                    |

---

## Adding a New Tool

1. Determine which group file applies (`scripts/setup/groups/`), or create a new one named `NN-groupname.sh`.

2. Add a `check_<tool>` function following this pattern:

```bash
check_mytool() {
  if ! has_command mytool; then
    log_tool_status "mytool" "" "missing"
    MISSING_TOOLS+=("mytool")
    return
  fi
  local output version
  output="$(mytool --version 2>&1 || true)"
  version="$(extract_version "$output" 'mytool ([0-9]+\.[0-9]+\.[0-9]+)')"
  if version_at_least "$version" "1.0.0"; then
    log_tool_status "mytool" "$version" "ok"
  else
    log_tool_status "mytool" "$version (need >=1.0)" "outdated"
    OUTDATED_TOOLS+=("mytool")
  fi
}
```

3. Add an `install_<tool>` function with `brew` / `apt` / `dnf` cases and a direct-download fallback.

4. Wire both into `group_check()` and `group_install()`.

5. Add the tool to `verify.sh`'s `VERIFY_TOOLS` array in the format:

```
"group|name|version_cmd|version_regex|min_version|optional(0/1)"
```

6. If it's a new group file, register it in `scripts/setup/setup.sh`'s `GROUP_FILES` associative array and `GROUP_ORDER` list.

---

## Uninstalling Tools

Out of scope for this script. Use your package manager directly:

```bash
# macOS
brew uninstall <package>
brew uninstall --cask <cask>

# Ubuntu/Debian
sudo apt remove <package>

# Fedora/RHEL
sudo dnf remove <package>
```

---

## Go Version Management (goenv)

The setup script detects `goenv` and prefers it over a direct brew/apt install.
Target version: **Go 1.26.2** (latest stable as of 2026-05-07; see `vault/02-Decisions/0047-go-version-policy.md`).

### Install goenv first (recommended)

```bash
brew install goenv
# Restart your shell so goenv shims are in PATH
exec $SHELL
# Then run setup
./setup.sh --only languages
```

### Why goenv

Developers who maintain multiple services may need different Go versions per project.
`brew install go` replaces the system-wide binary; `goenv` lets each project declare
its version in a `.go-version` file without affecting other projects.

### Without goenv

If goenv is not present, the script falls back to `brew install go` (macOS) or a direct
binary download from `go.dev/dl/` (Linux). The resulting Go binary is the latest available
from the package manager, which may lag behind `1.26.2`.

---

## Limitations

- **Windows native**: not supported. Use WSL2.
- **Requires bash 4+**: macOS ships bash 3.2. The script auto-detects
  `/opt/homebrew/bin/bash` or `/usr/local/bin/bash` and re-execs itself.
  If neither exists, it prompts you to run `brew install bash` first.
- **Docker on WSL**: Docker Desktop manages the engine via Windows integration.
  The script detects WSL and shows a reminder rather than trying to install Docker inside WSL.
- **Java Home**: Setting `JAVA_HOME` requires a shell restart to take effect in new terminals.
- **mc vs. Midnight Commander**: If `mc` resolves to Midnight Commander, the installer
  uses `mcli` as the binary name instead.
