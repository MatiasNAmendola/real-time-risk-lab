#!/usr/bin/env bash
# demo.sh — Print port-forward commands and URLs for all services
# Does NOT execute port-forwards — run each command in a separate terminal.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "$ROOT_DIR/../.." && pwd)"

source "$REPO_ROOT/scripts/lib/output.sh"
init_output "k8s-demo"

{
echo ""
echo "════════════════════════════════════════════════════════════════════════════"
echo "  naranja-poc — Service Access (run each port-forward in a separate shell)"
echo "════════════════════════════════════════════════════════════════════════════"
echo ""

ARGOCD_PASS=$(kubectl -n argocd get secret argocd-initial-admin \
  -o jsonpath='{.data.password}' 2>/dev/null | base64 -d 2>/dev/null || echo "run after cluster is up")

printf "  %-16s → %s\n" "ArgoCD" \
  "kubectl -n argocd port-forward svc/argocd-server 8081:80"
printf "  %-16s   URL: https://localhost:8081  (admin / %s)\n\n" "" "${ARGOCD_PASS}"

printf "  %-16s → %s\n" "Grafana" \
  "kubectl -n monitoring port-forward svc/kube-prometheus-stack-grafana 3000:80"
printf "  %-16s   URL: http://localhost:3000  (admin / prom-operator)\n\n" ""

printf "  %-16s → %s\n" "Prometheus" \
  "kubectl -n monitoring port-forward svc/kube-prometheus-stack-prometheus 9090:9090"
printf "  %-16s   URL: http://localhost:9090\n\n" ""

printf "  %-16s → %s\n" "Alertmanager" \
  "kubectl -n monitoring port-forward svc/kube-prometheus-stack-alertmanager 9093:9093"
printf "  %-16s   URL: http://localhost:9093\n\n" ""

printf "  %-16s → %s\n" "OpenObserve" \
  "kubectl -n openobserve port-forward svc/openobserve 5080:5080"
printf "  %-16s   URL: http://localhost:5080  (root@example.com / ${OPENOBSERVE_PASSWORD:-change-me-openobserve-local})\n\n" ""

printf "  %-16s → %s\n" "Redpanda Con." \
  "kubectl -n redpanda port-forward svc/redpanda-console 9000:8080"
printf "  %-16s   URL: http://localhost:9000\n\n" ""

printf "  %-16s → %s\n" "Argo Rollouts" \
  "kubectl -n argo-rollouts port-forward svc/argo-rollouts-dashboard 3100:3100"
printf "  %-16s   URL: http://localhost:3100\n\n" ""

printf "  %-16s → %s\n" "Risk Engine" \
  "kubectl -n risk port-forward svc/risk-engine 8090:8080"
printf "  %-16s   URL: http://localhost:8090/risk\n\n" ""

echo "────────────────────────────────────────────────────────────────────────────"
echo "  AWS Mocks"
echo "────────────────────────────────────────────────────────────────────────────"
echo ""

printf "  %-16s → %s\n" "MinIO Console" \
  "kubectl -n aws-mocks port-forward svc/minio 9001:9001"
printf "  %-16s   URL: http://localhost:9001  (${MINIO_ROOT_USER} / ${MINIO_ROOT_PASSWORD})\n\n" ""

printf "  %-16s → %s\n" "MinIO API (S3)" \
  "kubectl -n aws-mocks port-forward svc/minio 9000:9000"
printf "  %-16s   URL: http://localhost:9000\n\n" ""

printf "  %-16s → %s\n" "ElasticMQ UI" \
  "kubectl -n aws-mocks port-forward svc/elasticmq 9325:9325"
printf "  %-16s   URL: http://localhost:9325\n\n" ""

printf "  %-16s → %s\n" "ElasticMQ SQS" \
  "kubectl -n aws-mocks port-forward svc/elasticmq 9324:9324"
printf "  %-16s   URL: http://localhost:9324\n\n" ""

printf "  %-16s → %s\n" "Moto (SNS+SM)" \
  "kubectl -n aws-mocks port-forward svc/moto 5000:5000"
printf "  %-16s   URL: http://localhost:5000\n\n" ""

printf "  %-16s → %s\n" "OpenBao UI" \
  "kubectl -n aws-mocks port-forward svc/openbao 8200:8200"
printf "  %-16s   URL: http://localhost:8200/ui  (token: root)\n\n" ""

echo "════════════════════════════════════════════════════════════════════════════"
} 2>&1 | tee "$OUT_DIR/stdout.log"

finalize_output 0
