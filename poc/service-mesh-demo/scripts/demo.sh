#!/usr/bin/env bash
set -euo pipefail
curl -fsS --max-time 5 \
  -H 'Content-Type: application/json' \
  -H "X-Correlation-Id: demo-$(date +%s)" \
  -d '{"transactionId":"tx-service-mesh-1","customerId":"cust-42","amountCents":125000,"newDevice":true}' \
  http://localhost:8090/risk | jq .
