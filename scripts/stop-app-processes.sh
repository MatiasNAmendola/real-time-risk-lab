#!/usr/bin/env bash
# Stop app processes whose current working directory is inside a given repo path.
# This avoids port-based matching and avoids killing unrelated Bun/Node processes.

set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: scripts/stop-app-processes.sh --app-dir PATH [--yes] [--dry-run]

Stops repo-owned app processes by inspecting process current working directories.
It does not match by port number.

Targets:
- bun run src/main.ts
- bun --watch src/main.ts
- node dist/main.js
- Java main/shadowJar processes whose cwd is under PATH

Options:
  --app-dir PATH   Application directory to match by process cwd.
  --yes            Actually terminate. Without --yes this is a dry-run.
  --dry-run        Force dry-run.
USAGE
}

APP_DIR=""
YES=false
while [ $# -gt 0 ]; do
  case "$1" in
    --app-dir)
      APP_DIR="${2:-}"
      shift 2
      ;;
    --yes)
      YES=true
      shift
      ;;
    --dry-run)
      YES=false
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [ -z "$APP_DIR" ]; then
  echo "--app-dir is required" >&2
  usage >&2
  exit 2
fi

APP_DIR="$(cd "$APP_DIR" && pwd)"
SELF_PID="$$"

process_cwd() {
  local pid="$1"
  if command -v lsof >/dev/null 2>&1; then
    lsof -a -p "$pid" -d cwd -Fn 2>/dev/null | sed -n 's/^n//p' | head -1 && return 0
  fi
  if [ -L "/proc/$pid/cwd" ]; then
    readlink "/proc/$pid/cwd" 2>/dev/null && return 0
  fi
  return 1
}

is_target_command() {
  local cmd="$1"
  case "$cmd" in
    *"bun run src/main.ts"*|*"bun --watch src/main.ts"*|*"node dist/main.js"*) return 0 ;;
    *"java "*"/build/libs/"*|*"java "*"GradleWrapperMain"*) return 0 ;;
    *) return 1 ;;
  esac
}

matches=()
while IFS= read -r line; do
  # shellcheck disable=SC2206
  parts=($line)
  pid="${parts[0]:-}"
  [ -n "$pid" ] || continue
  [ "$pid" != "$SELF_PID" ] || continue
  cmd="${line#* * * * }"
  if ! is_target_command "$cmd"; then
    continue
  fi
  cwd="$(process_cwd "$pid" || true)"
  if [ -n "$cwd" ] && [[ "$cwd" == "$APP_DIR"* ]]; then
    matches+=("$pid|$cwd|$cmd")
  fi
done < <(ps ax -o pid=,ppid=,stat=,etime=,command=)

if [ "${#matches[@]}" -eq 0 ]; then
  echo "No app processes found under $APP_DIR"
  exit 0
fi

printf '%7s  %-45s  %s\n' "PID" "CWD" "COMMAND"
printf '%7s  %-45s  %s\n' "-------" "---------------------------------------------" "----------------------------------------"
for row in "${matches[@]}"; do
  IFS='|' read -r pid cwd cmd <<<"$row"
  printf '%7s  %-45s  %s\n' "$pid" "$cwd" "$cmd"
done

if [ "$YES" != true ]; then
  echo ""
  echo "Dry-run: re-run with --yes to stop these processes."
  exit 0
fi

for row in "${matches[@]}"; do
  IFS='|' read -r pid _cwd _cmd <<<"$row"
  if kill -TERM "$pid" 2>/dev/null; then
    echo "sent SIGTERM pid=$pid"
  else
    echo "could not terminate pid=$pid" >&2
  fi
done
