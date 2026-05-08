// smoke.js — 1 VU, 30s. Confirms the service starts and accepts /risk.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { riskEndpoint } from '../lib/config.js';
import { buildRiskRequest } from '../lib/payload.js';
import { sloRelaxed } from '../lib/thresholds.js';

export const options = {
  vus: 1,
  duration: __ENV.DURATION || '30s',
  thresholds: sloRelaxed,
  summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  const url = riskEndpoint();
  const req = buildRiskRequest();
  const res = http.post(url, req.body, { headers: req.headers });
  check(res, {
    'status is 2xx': (r) => r.status >= 200 && r.status < 300,
    'has decision': (r) => {
      try { return JSON.parse(r.body).decision !== undefined; } catch { return false; }
    },
  });
  sleep(0.1);
}
