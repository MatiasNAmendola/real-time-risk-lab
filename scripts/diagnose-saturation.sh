#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PROJECT_RE='risk|vertx|service-mesh|vertx-layer-as-pod-eventbus'

echo "== Java processes that look project-related =="
ps -axo pid,ppid,stat,etime,command | awk -v re="$PROJECT_RE" 'NR==1 || tolower($0) ~ re {print}'

echo
echo "== Zombie processes =="
ps -axo pid,ppid,stat,etime,command | awk 'NR==1 || $3 ~ /Z/ {print}'

echo
echo "== Docker restart loops / unhealthy / starting > bounded diagnostics =="
if command -v docker >/dev/null 2>&1; then
  docker ps -a --format '{{.ID}} {{.Names}} {{.Status}} {{.Image}}' \
    | awk 'BEGIN{IGNORECASE=1} /restarting|unhealthy|starting/ {print}' || true
  echo
  echo "== Dangling Docker volumes (project-looking names only) =="
  docker volume ls -qf dangling=true | grep -E "$PROJECT_RE" || true
  echo
  echo "== Compose projects in this repo with containers =="
  docker ps -a --filter "label=com.docker.compose.project.working_dir=$ROOT" \
    --format '{{.Names}} {{.Status}}' || true
else
  echo "docker not found"
fi

echo
echo "Tip: cleanup safe del PoC: cd poc/<poc> && docker compose down --remove-orphans."
echo "Tip: prune seguro de dangling no usados: docker volume prune -f && docker network prune -f."
