// payload.js — generates valid RiskRequest payloads.
//
// Schema mirrors what the four services accept (bare-javac, monolith,
// vertx-platform, distributed). Fields:
//   transactionId    UUID v4
//   customerId       string (cust_NNNN)
//   amountCents      integer in [100, 5_000_000]
//   correlationId    UUID v4 (also passed as X-Correlation-Id header)
//   idempotencyKey   UUID v4 (passed as Idempotency-Key header)

import { uuidv4, randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

export function buildRiskRequest() {
  const txId = uuidv4();
  const corr = uuidv4();
  const idem = uuidv4();
  const customer = `cust_${String(randomIntBetween(1, 9999)).padStart(4, '0')}`;
  const amount = randomIntBetween(100, 5_000_000);
  return {
    body: JSON.stringify({
      transactionId: txId,
      customerId: customer,
      amountCents: amount,
      correlationId: corr,
      idempotencyKey: idem,
    }),
    headers: {
      'Content-Type': 'application/json',
      'X-Correlation-Id': corr,
      'Idempotency-Key': idem,
    },
  };
}
