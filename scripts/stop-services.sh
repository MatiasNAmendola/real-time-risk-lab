#!/usr/bin/env bash
# Stop repo-owned runtime services without matching hardcoded ports.
# It stops app processes by cwd and Docker Compose stacks by compose file/project.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
YES=false
DRY_RUN=true
INCLUDE_K8S=false
WITH_VOLUMES=false
TARGET="all"

usage() {
  cat <<'USAGE'
Usage: ./scripts/stop-services.sh [target] [--yes] [--include-k8s] [--volumes]

Targets:
  all                         Stop app processes and all repo Docker Compose stacks (default)
  apps                        Stop only local app processes started from repo app dirs
  compose                     Stop only repo Docker Compose stacks
  nestjs-distributed-transactions
  hono-distributed-transactions
  typescript-transactional-pocs
  vertx                       Stop Vert.x compose stacks
  dashboard                   Stop only dashboard compose stack
  k8s                         Stop k8s-local only

Options:
  --yes                       Execute. Default is dry-run where possible.
  --include-k8s               Include k8s-local when target is all.
  --volumes                   Pass --volumes to docker compose down.

Examples:
  ./scripts/stop-services.sh all --yes
  ./scripts/stop-services.sh typescript-transactional-pocs --yes
  ./scripts/stop-services.sh compose --yes --volumes
  ./nx stop all --yes
USAGE
}

while [ $# -gt 0 ]; do
  case "$1" in
    --yes)
      YES=true
      DRY_RUN=false
      shift
      ;;
    --dry-run)
      YES=false
      DRY_RUN=true
      shift
      ;;
    --include-k8s)
      INCLUDE_K8S=true
      shift
      ;;
    --volumes|-v)
      WITH_VOLUMES=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      TARGET="$1"
      shift
      ;;
  esac
done

run() {
  echo "+ $*"
  if [ "$DRY_RUN" != true ]; then
    "$@"
  fi
}

compose_down() {
  local files_arg=()
  local f
  for f in "$@"; do
    files_arg+=("-f" "$REPO_ROOT/$f")
  done
  if [ "$WITH_VOLUMES" = true ]; then
    run docker compose "${files_arg[@]}" down --remove-orphans --volumes
  else
    run docker compose "${files_arg[@]}" down --remove-orphans
  fi
}

stop_app_dir() {
  local dir="$1"
  if [ "$YES" = true ]; then
    run "$REPO_ROOT/scripts/stop-app-processes.sh" --app-dir "$REPO_ROOT/$dir" --yes
  else
    "$REPO_ROOT/scripts/stop-app-processes.sh" --app-dir "$REPO_ROOT/$dir"
  fi
}

stop_nestjs() {
  stop_app_dir "poc/nestjs-distributed-transactions"
  compose_down "poc/nestjs-distributed-transactions/docker-compose.yml"
}

stop_hono() {
  stop_app_dir "poc/hono-distributed-transactions"
  compose_down "poc/hono-distributed-transactions/docker-compose.yml"
}

stop_typescript() {
  stop_nestjs
  stop_hono
}

stop_vertx_compose() {
  compose_down "compose/docker-compose.yml" "poc/vertx-layer-as-pod-eventbus/compose.override.yml"
  compose_down "compose/docker-compose.yml" "poc/vertx-monolith-inprocess/compose.override.yml"
  compose_down "compose/docker-compose.yml" "poc/vertx-layer-as-pod-http/compose.override.yml"
  compose_down "compose/docker-compose.yml" "poc/vertx-layer-as-pod-eventbus/compose.override.yml" "poc/vertx-monolith-inprocess/compose.override.yml" "poc/vertx-layer-as-pod-http/compose.override.yml"
  compose_down "compose/docker-compose.yml"
  if [ -f "$REPO_ROOT/poc/vertx-service-mesh-bounded-contexts/docker-compose.yml" ]; then
    compose_down "poc/vertx-service-mesh-bounded-contexts/docker-compose.yml"
  fi
}

stop_other_compose() {
  [ -f "$REPO_ROOT/dashboard/docker-compose.yml" ] && compose_down "dashboard/docker-compose.yml"
  [ -f "$REPO_ROOT/poc/kafka-s3-tansu/compose.override.yml" ] && compose_down "compose/docker-compose.yml" "poc/kafka-s3-tansu/compose.override.yml"
  [ -f "$REPO_ROOT/compose/docker-compose.dev-tools.yml" ] && compose_down "compose/docker-compose.dev-tools.yml"
}

stop_k8s() {
  local script="$REPO_ROOT/poc/k8s-local/scripts/down.sh"
  if [ -f "$script" ]; then
    run bash "$script"
  else
    echo "k8s down script not found: $script" >&2
  fi
}

case "$TARGET" in
  all)
    stop_typescript
    stop_vertx_compose
    stop_other_compose
    if [ "$INCLUDE_K8S" = true ]; then
      stop_k8s
    fi
    ;;
  apps)
    stop_typescript
    ;;
  compose)
    stop_typescript
    stop_vertx_compose
    stop_other_compose
    ;;
  nestjs-distributed-transactions|nestjs)
    stop_nestjs
    ;;
  hono-distributed-transactions|hono)
    stop_hono
    ;;
  typescript-transactional-pocs|ts)
    stop_typescript
    ;;
  vertx)
    stop_vertx_compose
    ;;
  dashboard)
    [ -f "$REPO_ROOT/dashboard/docker-compose.yml" ] && compose_down "dashboard/docker-compose.yml"
    ;;
  k8s)
    stop_k8s
    ;;
  *)
    echo "Unknown target: $TARGET" >&2
    usage >&2
    exit 2
    ;;
esac

if [ "$DRY_RUN" = true ]; then
  echo ""
  echo "Dry-run complete. Re-run with --yes to execute."
fi
