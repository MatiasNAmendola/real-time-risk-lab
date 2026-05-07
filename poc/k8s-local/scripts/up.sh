#!/usr/bin/env bash
# up.sh — Idempotent bootstrap for naranja-poc cluster
# Usage: ./scripts/up.sh [--provider orbstack|k3d]
# Prerequisites: brew install helm kubectl
#   k3d provider: brew install k3d
#   orbstack provider: install OrbStack from https://orbstack.dev

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "$ROOT_DIR/../.." && pwd)"
CLUSTER_NAME="naranja-poc"

source "$REPO_ROOT/scripts/lib/output.sh"
init_output "k8s-up"

log()  { echo "[up.sh] $*" | tee -a "$OUT_DIR/stdout.log"; }
ok()   { echo "[up.sh] OK: $*" | tee -a "$OUT_DIR/stdout.log"; }
warn() { echo "[up.sh] WARN: $*" | tee -a "$OUT_DIR/stdout.log"; }
err()  { echo "[up.sh] ERROR: $*" | tee -a "$OUT_DIR/stderr.log" >&2; finalize_output 1; exit 1; }

# ─── Parse args ───────────────────────────────────────────────────────────────
PROVIDER_FLAG=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --provider)
      PROVIDER_FLAG="$2"
      shift 2
      ;;
    --provider=*)
      PROVIDER_FLAG="${1#--provider=}"
      shift
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

# ─── Detect provider ──────────────────────────────────────────────────────────
detect_provider() {
  if command -v orb >/dev/null 2>&1 && orb status >/dev/null 2>&1; then
    echo "orbstack"
  else
    echo "k3d"
  fi
}

if [[ -n "${PROVIDER_FLAG}" ]]; then
  K8S_PROVIDER="${PROVIDER_FLAG}"
elif [[ -n "${K8S_PROVIDER:-}" ]]; then
  : # already set via env
else
  K8S_PROVIDER="$(detect_provider)"
  log "Autodetected provider: ${K8S_PROVIDER}"
fi

if [[ "${K8S_PROVIDER}" != "orbstack" && "${K8S_PROVIDER}" != "k3d" ]]; then
  err "Unknown K8S_PROVIDER '${K8S_PROVIDER}'. Valid values: orbstack, k3d"
fi

log "Using provider: ${K8S_PROVIDER}"

# ─── Dependency check (common) ────────────────────────────────────────────────
for cmd in helm kubectl; do
  if ! command -v "$cmd" &>/dev/null; then
    err "'$cmd' not found. Install with: brew install helm kubectl"
  fi
done

# ─── Bootstrap functions ──────────────────────────────────────────────────────

bootstrap_orbstack() {
  if ! command -v orb &>/dev/null; then
    err "OrbStack not found. Install from https://orbstack.dev then re-run."
  fi

  if ! orb status &>/dev/null; then
    err "OrbStack is not running. Run: orb start  (or open OrbStack from the menu bar)"
  fi

  if ! kubectl config get-contexts orbstack &>/dev/null; then
    log "Enabling Kubernetes in OrbStack..."
    if orb config set k8s.enabled true 2>/dev/null; then
      log "Kubernetes enabled. Waiting 60s for control plane..."
      sleep 60
    else
      warn "Could not enable k8s via CLI. Enable it manually:" \
           "OrbStack menu → Settings → Kubernetes → Enable Kubernetes"
      echo "[up.sh] WARN: Then re-run this script." >&2
      exit 1
    fi
  fi

  kubectl config use-context orbstack
  log "kubectl context: orbstack"

  log "Waiting for node to be Ready (up to 120s)..."
  kubectl wait --for=condition=Ready node --all --timeout=120s
  ok "OrbStack k8s node ready."
}

bootstrap_k3d() {
  if ! command -v k3d &>/dev/null; then
    err "'k3d' not found. Install with: brew install k3d"
  fi

  if k3d cluster get "${CLUSTER_NAME}" &>/dev/null; then
    warn "Cluster '${CLUSTER_NAME}' already exists — skipping create."
  else
    log "Creating k3d cluster '${CLUSTER_NAME}'..."
    k3d cluster create "${CLUSTER_NAME}" \
      --servers 1 \
      --agents 2 \
      --port "8080:80@loadbalancer" \
      --port "8443:443@loadbalancer" \
      --wait
    ok "Cluster created."
  fi

  kubectl config use-context "k3d-${CLUSTER_NAME}"
  log "kubectl context: k3d-${CLUSTER_NAME}"
}

# ─── 1. Provider bootstrap ────────────────────────────────────────────────────
case "${K8S_PROVIDER}" in
  orbstack) bootstrap_orbstack ;;
  k3d)      bootstrap_k3d ;;
esac

# ─── 2. Namespaces ────────────────────────────────────────────────────────────
log "Applying namespaces..."
kubectl apply -f "${ROOT_DIR}/addons/00-namespaces.yaml" 2>&1 | tee -a "$OUT_DIR/stdout.log"
ok "Namespaces ready."

# ─── 3. Helm repos ────────────────────────────────────────────────────────────
log "Adding Helm repos..."
{
  helm repo add argo             https://argoproj.github.io/argo-helm 2>/dev/null || true
  helm repo add external-secrets https://charts.external-secrets.io   2>/dev/null || true
  helm repo add redpanda         https://charts.redpanda.com/          2>/dev/null || true
  helm repo add openobserve      https://charts.openobserve.ai         2>/dev/null || true
  helm repo update
} 2>&1 | tee -a "$OUT_DIR/stdout.log"
ok "Helm repos updated."

# ─── Helper: install with 1 retry on timeout ──────────────────────────────────
helm_install() {
  local name="$1" chart="$2" ns="$3" values="$4"
  log "Installing ${name} from ${chart}..."
  local attempt=0
  while [[ $attempt -lt 2 ]]; do
    if helm upgrade --install "${name}" "${chart}" \
        --namespace "${ns}" \
        -f "${values}" \
        --timeout 5m \
        --atomic \
        --wait 2>&1 | tee -a "$OUT_DIR/stdout.log"; then
      ok "${name} installed."
      return 0
    fi
    attempt=$((attempt + 1))
    warn "Attempt ${attempt} failed for ${name}. Retrying..."
    sleep 5
  done
  err "Failed to install ${name} after 2 attempts."
}

helm_install argocd argo/argo-cd argocd "${ROOT_DIR}/addons/10-argocd-values.yaml"
helm_install argo-rollouts argo/argo-rollouts argo-rollouts "${ROOT_DIR}/addons/20-argo-rollouts-values.yaml"
helm_install external-secrets external-secrets/external-secrets external-secrets "${ROOT_DIR}/addons/40-external-secrets-values.yaml"

log "Waiting for ESO webhook..."
kubectl wait --for=condition=ready pod \
  -l app.kubernetes.io/name=external-secrets-webhook \
  -n external-secrets \
  --timeout=120s 2>&1 | tee -a "$OUT_DIR/stdout.log" || warn "ESO webhook not ready in time — continuing anyway."

log "Applying ClusterSecretStore + source secret..."
kubectl apply -f "${ROOT_DIR}/addons/41-cluster-secret-store.yaml" 2>&1 | tee -a "$OUT_DIR/stdout.log"
ok "ClusterSecretStore ready."

helm_install redpanda redpanda/redpanda redpanda "${ROOT_DIR}/addons/50-redpanda-values.yaml"
helm_install openobserve openobserve/openobserve-standalone openobserve "${ROOT_DIR}/addons/60-openobserve-values.yaml"

log "Deploying AWS mock services..."
kubectl apply -f "${ROOT_DIR}/addons/70-aws-mocks.yaml" 2>&1 | tee -a "$OUT_DIR/stdout.log"
ok "AWS mock deployments applied."

log "Waiting for AWS mock pods to be ready..."
kubectl wait --for=condition=ready pod \
  -l app.kubernetes.io/part-of=aws-mocks \
  -n aws-mocks \
  --timeout=180s 2>&1 | tee -a "$OUT_DIR/stdout.log" || warn "Some AWS mock pods not ready in time — init job may retry."

log "Running AWS mocks init job..."
kubectl delete job aws-mocks-init -n aws-mocks --ignore-not-found=true 2>&1 | tee -a "$OUT_DIR/stdout.log"
kubectl apply -f "${ROOT_DIR}/addons/71-aws-mocks-init.yaml" 2>&1 | tee -a "$OUT_DIR/stdout.log"

log "Waiting for init job to complete (up to 3 min)..."
kubectl wait --for=condition=complete job/aws-mocks-init \
  -n aws-mocks \
  --timeout=180s 2>&1 | tee -a "$OUT_DIR/stdout.log" || warn "AWS mocks init job did not complete in time."
ok "AWS mocks initialized."

log "Applying ArgoCD project and application manifests..."
kubectl apply -f "${ROOT_DIR}/argocd/project.yaml" 2>&1 | tee -a "$OUT_DIR/stdout.log"
kubectl apply -f "${ROOT_DIR}/argocd/analysis-templates/" -n risk 2>&1 | tee -a "$OUT_DIR/stdout.log" || true
kubectl apply -f "${ROOT_DIR}/argocd/application-risk-engine.yaml" 2>&1 | tee -a "$OUT_DIR/stdout.log"
ok "ArgoCD application registered."

log "Waiting for critical pods to be ready..."
kubectl wait --for=condition=ready pod \
  -l app.kubernetes.io/name=argocd-server \
  -n argocd --timeout=120s 2>&1 | tee -a "$OUT_DIR/stdout.log" || warn "ArgoCD server not ready"

# Capture cluster state for inspection
{
  echo ""
  echo "==> cluster state: kubectl get pods -A"
  kubectl get pods -A 2>&1
  echo ""
  echo "==> cluster state: helm list -A"
  helm list -A 2>&1
  echo ""
  echo "==> cluster state: kubectl get svc -A"
  kubectl get svc -A 2>&1
} > "$OUT_DIR/cluster-state.txt"

ARGOCD_PASS=$(kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath='{.data.password}' 2>/dev/null | base64 -d 2>/dev/null || echo "(not yet available)")

{
  echo ""
  echo "════════════════════════════════════════════════════════════════"
  echo "  naranja-poc cluster is UP  (provider: ${K8S_PROVIDER})"
  echo "════════════════════════════════════════════════════════════════"
  echo ""
  echo "  ArgoCD initial admin password: ${ARGOCD_PASS}"
  echo ""
  echo "  Run './scripts/demo.sh' for all port-forward commands."
  echo ""
} | tee -a "$OUT_DIR/stdout.log"

finalize_output 0
