// spike.js — 0→200 VUs in 30s, hold 1 min, ramp down 30s.
// Validates auto-scaling and circuit-breaker behavior under sudden burst.
import http from 'k6/http';
import { check } from 'k6';
import { riskEndpoint } from '../lib/config.js';
import { buildRiskRequest } from '../lib/payload.js';

export const options = {
  stages: [
    { duration: '30s', target: 200 },
    { duration: '1m',  target: 200 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.15'],
    http_req_duration: ['p(99)<2000'],
  },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  const req = buildRiskRequest();
  const res = http.post(riskEndpoint(), req.body, { headers: req.headers });
  check(res, { 'no 5xx': (r) => r.status < 500 });
}
