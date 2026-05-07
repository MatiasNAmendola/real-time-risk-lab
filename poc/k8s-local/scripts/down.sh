#!/usr/bin/env bash
# down.sh — Tear down the naranja-poc cluster
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "$ROOT_DIR/../.." && pwd)"
CLUSTER_NAME="naranja-poc"
FULL=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --full) FULL=true; shift ;;
    *) echo "Unknown argument: $1" >&2; exit 1 ;;
  esac
done

source "$REPO_ROOT/scripts/lib/output.sh"
init_output "k8s-down"

log()  { echo "[down.sh] $*" | tee -a "$OUT_DIR/stdout.log"; }
warn() { echo "[down.sh] WARN: $*" | tee -a "$OUT_DIR/stdout.log"; }
ok()   { echo "[down.sh] OK: $*" | tee -a "$OUT_DIR/stdout.log"; }

POC_NAMESPACES=(
  argocd
  argo-rollouts
  monitoring
  external-secrets
  redpanda
  openobserve
  risk
  secrets-source
  aws-mocks
)

CURRENT_CTX="$(kubectl config current-context 2>/dev/null || echo "")"
log "Current kubectl context: ${CURRENT_CTX}"

case "${CURRENT_CTX}" in
  orbstack)
    log "OrbStack provider detected — deleting PoC namespaces only."
    for ns in "${POC_NAMESPACES[@]}"; do
      if kubectl get namespace "${ns}" &>/dev/null; then
        log "Deleting namespace: ${ns}"
        kubectl delete namespace "${ns}" --timeout=60s 2>&1 | tee -a "$OUT_DIR/stdout.log" \
          || warn "Namespace ${ns} did not terminate cleanly."
      else
        warn "Namespace '${ns}' not found — skipping."
      fi
    done
    ok "PoC namespaces deleted."

    if [[ "${FULL}" == "true" ]]; then
      log "--full flag set: disabling Kubernetes in OrbStack..."
      if orb config set k8s.enabled false 2>/dev/null; then
        log "Kubernetes disabled in OrbStack."
      else
        warn "Could not disable k8s via CLI. Disable manually: OrbStack → Settings → Kubernetes"
      fi
    fi
    ;;

  k3d-"${CLUSTER_NAME}")
    log "k3d provider detected — deleting cluster '${CLUSTER_NAME}'..."
    if k3d cluster get "${CLUSTER_NAME}" &>/dev/null; then
      k3d cluster delete "${CLUSTER_NAME}" 2>&1 | tee -a "$OUT_DIR/stdout.log"
      log "Cluster deleted."
    else
      warn "Cluster '${CLUSTER_NAME}' does not exist — nothing to do."
    fi
    ;;

  "")
    log "No current kubectl context found. Nothing to do."
    ;;

  *)
    warn "Context '${CURRENT_CTX}' is not a known PoC context."
    ;;
esac

finalize_output 0
