#!/usr/bin/env bash
set -euo pipefail

APP_DIR="$(cd "$(dirname "$0")/.." && pwd)"
REPO_ROOT="$(cd "$APP_DIR/../.." && pwd)"
YES=false
WITH_VOLUMES=false

while [ $# -gt 0 ]; do
  case "$1" in
    --yes) YES=true; shift ;;
    --volumes|-v) WITH_VOLUMES=true; shift ;;
    -h|--help)
      cat <<USAGE
Usage: ./scripts/stop.sh [--yes] [--volumes]

Stops this PoC without matching hardcoded ports:
- local Bun/Node app processes whose cwd is this app directory;
- this app's Docker Compose stack.

Default is dry-run for process termination. Compose down is printed in dry-run mode.
USAGE
      exit 0
      ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done

args=(--app-dir "$APP_DIR")
if [ "$YES" = true ]; then
  args+=(--yes)
fi
"$REPO_ROOT/scripts/stop-app-processes.sh" "${args[@]}"

compose_args=(down --remove-orphans)
if [ "$WITH_VOLUMES" = true ]; then
  compose_args+=(--volumes)
fi

echo "+ docker compose -f $APP_DIR/docker-compose.yml ${compose_args[*]}"
if [ "$YES" = true ]; then
  docker compose -f "$APP_DIR/docker-compose.yml" "${compose_args[@]}"
else
  echo "Dry-run: re-run with --yes to stop compose resources."
fi
