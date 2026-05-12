#!/usr/bin/env bash
# invoke_ts.sh — evaluate a risk request via the TypeScript SDK and print JSON.
#
# Usage: invoke_ts.sh <transactionId> <amount> <newDevice> <baseUrl>
#
# Output (stdout): {"transactionId":"...","decision":"...","reason":"..."}
# Exit 0 on success, non-zero on error.

set -euo pipefail

TX_ID="${1:?transactionId required}"
AMOUNT="${2:?amount required}"
NEW_DEVICE="${3:-false}"
BASE_URL="${4:-http://localhost:8080}"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
SDK_DIR="${REPO_ROOT}/sdks/risk-client-typescript"

# Build the SDK if dist/ does not exist.
if [ ! -d "${SDK_DIR}/dist" ]; then
  (cd "${SDK_DIR}" && npm run build --silent 2>/dev/null || npm install --silent && npm run build --silent)
fi

# Inline Node script — evaluates the request and prints JSON to stdout.
node - <<EOF
const { RiskClient } = require('${SDK_DIR}/dist/index.js');

// Must be set before constructing RiskClient; channels resolve endpoints in constructors.
process.env.RISK_BASE_URL = '${BASE_URL}';

const client = new RiskClient({
  environment: 'LOCAL',
  apiKey: process.env.RISK_CLIENT_API_KEY || 'change-me-client-api-key',
  timeoutMs: 10000,
});

const req = {
  transactionId:  '${TX_ID}',
  customerId:     'cust-1',
  amountCents:     ${AMOUNT},
  correlationId:  'corr-${TX_ID}',
  idempotencyKey: 'idem-${TX_ID}',
  newDevice:      ${NEW_DEVICE},
  deviceId:       '${NEW_DEVICE}' === 'true' ? 'new-device-ts-' + Date.now() : 'known-dev-1',
  merchantId:     'merch-1',
  channel:        'WEB',
};

client.sync.evaluate(req)
  .then(d => {
    process.stdout.write(JSON.stringify({
      transactionId: d.transactionId,
      decision:      d.decision,
      reason:        d.reason,
    }) + '\n');
  })
  .catch(err => {
    process.stderr.write('TS SDK error: ' + err.message + '\n');
    process.exit(1);
  });
EOF
