#!/usr/bin/env bash
# status.sh — Show all pod statuses across namespaces and capture to out/
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "$ROOT_DIR/../.." && pwd)"

source "$REPO_ROOT/scripts/lib/output.sh"
init_output "k8s-status"

{
  echo "=== All pods across namespaces ==="
  kubectl get pods -A --sort-by='.metadata.namespace'

  echo ""
  echo "=== ArgoCD Applications ==="
  kubectl get applications -n argocd 2>/dev/null || echo "(ArgoCD not installed yet)"

  echo ""
  echo "=== Argo Rollouts ==="
  kubectl get rollouts -n risk 2>/dev/null || echo "(no rollouts in risk namespace yet)"

  echo ""
  echo "=== External Secrets ==="
  kubectl get externalsecrets -A 2>/dev/null || echo "(ESO not installed yet)"

  echo ""
  echo "=== Services ==="
  kubectl get svc -A 2>/dev/null || true

  echo ""
  echo "=== Helm releases ==="
  helm list -A 2>/dev/null || echo "(helm not available)"
} 2>&1 | tee "$OUT_DIR/stdout.log" > "$OUT_DIR/cluster-state.txt"

cat "$OUT_DIR/cluster-state.txt"

finalize_output 0
