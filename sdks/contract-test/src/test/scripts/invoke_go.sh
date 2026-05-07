#!/usr/bin/env bash
# invoke_go.sh — evaluate a risk request via the Go SDK and print JSON.
#
# Usage: invoke_go.sh <transactionId> <amount> <newDevice> <baseUrl>
#
# Output (stdout): {"transactionId":"...","decision":"...","reason":"..."}
# Exit 0 on success, non-zero on error.

set -euo pipefail

TX_ID="${1:?transactionId required}"
AMOUNT="${2:?amount required}"
NEW_DEVICE="${3:-false}"
BASE_URL="${4:-http://localhost:8080}"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../../.." && pwd)"
SDK_DIR="${REPO_ROOT}/sdks/risk-client-go"

# Build a temporary evaluator binary.
RUNNER_DIR="$(mktemp -d)"
trap 'rm -rf "${RUNNER_DIR}"' EXIT

DEVICE_ID="known-dev-1"
if [ "${NEW_DEVICE}" = "true" ]; then
  DEVICE_ID="new-device-go-$(date +%s%N)"
fi

cat > "${RUNNER_DIR}/main.go" <<GOEOF
package main

import (
  "context"
  "encoding/json"
  "fmt"
  "os"
  "time"

  riskclient "github.com/naranjax/risk-client"
)

func main() {
  cfg := riskclient.Config{
    Environment: riskclient.Local,
    APIKey:      "test",
    Timeout:     10 * time.Second,
    Retry:       riskclient.ExponentialBackoff(),
  }
  client := riskclient.NewWithServerOverride(cfg, "${BASE_URL}", nil)

  req := riskclient.RiskRequest{
    TransactionID:  "${TX_ID}",
    CustomerID:     "cust-1",
    AmountCents:    ${AMOUNT},
    CorrelationID:  "corr-${TX_ID}",
    IdempotencyKey: "idem-${TX_ID}",
    NewDevice:      ${NEW_DEVICE},
    DeviceID:       "${DEVICE_ID}",
    MerchantID:     "merch-1",
    Channel:        "WEB",
  }

  decision, err := client.Sync.Evaluate(context.Background(), req)
  if err != nil {
    fmt.Fprintf(os.Stderr, "Go SDK error: %v\n", err)
    os.Exit(1)
  }

  out := map[string]string{
    "transactionId": decision.TransactionID,
    "decision":      decision.Decision,
    "reason":        decision.Reason,
  }
  enc, _ := json.Marshal(out)
  fmt.Println(string(enc))
}
GOEOF

# Copy go.mod / go.sum for module resolution.
cp "${SDK_DIR}/go.mod" "${RUNNER_DIR}/go.mod"
cp "${SDK_DIR}/go.sum" "${RUNNER_DIR}/go.sum"

# Replace module name so the runner can import the SDK as a local replace.
MODULE_NAME="$(head -1 "${SDK_DIR}/go.mod" | awk '{print $2}')"
sed -i.bak "s|^module .*|module runner|" "${RUNNER_DIR}/go.mod"
printf '\nreplace %s => %s\n' "${MODULE_NAME}" "${SDK_DIR}" >> "${RUNNER_DIR}/go.mod"
# Re-add the sdk as explicit require.
printf '\nrequire %s v0.0.0\n' "${MODULE_NAME}" >> "${RUNNER_DIR}/go.mod"

cd "${RUNNER_DIR}"
go run ./main.go
