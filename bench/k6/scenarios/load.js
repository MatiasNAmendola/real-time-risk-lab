// load.js — 32 VUs, 2 min sustained. SLO: p99 < 300ms.
import http from 'k6/http';
import { check } from 'k6';
import { riskEndpoint } from '../lib/config.js';
import { buildRiskRequest } from '../lib/payload.js';
import { sloStrict } from '../lib/thresholds.js';

export const options = {
  vus: parseInt(__ENV.VUS || '32', 10),
  duration: __ENV.DURATION || '2m',
  thresholds: sloStrict,
  summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  const req = buildRiskRequest();
  const res = http.post(riskEndpoint(), req.body, { headers: req.headers });
  check(res, {
    'status is 2xx': (r) => r.status >= 200 && r.status < 300,
  });
}
