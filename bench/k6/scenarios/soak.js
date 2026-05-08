// soak.js — 16 VUs, 30 min. Detects memory leaks and resource exhaustion.
import http from 'k6/http';
import { check } from 'k6';
import { riskEndpoint } from '../lib/config.js';
import { buildRiskRequest } from '../lib/payload.js';
import { sloSoak } from '../lib/thresholds.js';

export const options = {
  vus: parseInt(__ENV.VUS || '16', 10),
  duration: __ENV.DURATION || '30m',
  thresholds: sloSoak,
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  const req = buildRiskRequest();
  const res = http.post(riskEndpoint(), req.body, { headers: req.headers });
  check(res, { 'status is 2xx': (r) => r.status >= 200 && r.status < 300 });
}
