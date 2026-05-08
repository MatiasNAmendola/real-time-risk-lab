// stress.js — ramp 0→100 VUs in 5 min. Find the knee of the curve.
import http from 'k6/http';
import { check } from 'k6';
import { riskEndpoint } from '../lib/config.js';
import { buildRiskRequest } from '../lib/payload.js';

export const options = {
  stages: [
    { duration: '1m', target: 25 },
    { duration: '1m', target: 50 },
    { duration: '1m', target: 75 },
    { duration: '2m', target: 100 },
  ],
  thresholds: {
    // Stress runs intentionally push past SLO; track but don't gate.
    http_req_duration: ['p(99)<1500'],
    http_req_failed: ['rate<0.10'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  const req = buildRiskRequest();
  const res = http.post(riskEndpoint(), req.body, { headers: req.headers });
  check(res, { 'status is 2xx': (r) => r.status >= 200 && r.status < 300 });
}
