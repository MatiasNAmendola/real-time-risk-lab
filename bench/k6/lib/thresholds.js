// thresholds.js — SLO assertions shared by all scenarios.
//
// Production SLO for the risk decision platform:
//   - p99 latency < 300 ms (R1 of risk-decision SLA)
//   - error rate < 1%
// Smoke runs relax thresholds because cold-start dominates the first 30s.

export const sloStrict = {
  http_req_duration: ['p(95)<200', 'p(99)<300'],
  http_req_failed: ['rate<0.01'],
  checks: ['rate>0.99'],
};

export const sloRelaxed = {
  http_req_duration: ['p(99)<800'],
  http_req_failed: ['rate<0.05'],
  checks: ['rate>0.95'],
};

// soak: detect leaks via slope, not just absolute p99.
export const sloSoak = {
  http_req_duration: ['p(99)<400'],
  http_req_failed: ['rate<0.02'],
  checks: ['rate>0.98'],
};
