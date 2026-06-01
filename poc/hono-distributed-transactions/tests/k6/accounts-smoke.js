import http from 'k6/http';
import { check, sleep } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://127.0.0.1:43103';
const duration = __ENV.K6_DURATION || '10s';
const vus = Number(__ENV.K6_VUS || '2');

export const options = {
  vus,
  duration,
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    checks: ['rate==1.0'],
  },
  tags: {
    scenario: 'typescript-cqrs-event-sourcing-smoke',
  },
};

export default function () {
  const accountId = `k6-account-${__VU}-${__ITER}-${Date.now()}`;
  const headers = { 'Content-Type': 'application/json', 'X-Correlation-Id': `k6-${accountId}` };

  const open = http.post(`${baseUrl}/accounts/${accountId}/open`, null, { headers });
  check(open, {
    'open account returns 2xx': (response) => response.status >= 200 && response.status < 300,
  });

  const deposit = http.post(`${baseUrl}/accounts/${accountId}/deposit`, JSON.stringify({ amount: 10, currency: 'ARS' }), { headers });
  check(deposit, {
    'deposit returns 2xx': (response) => response.status >= 200 && response.status < 300,
  });

  const account = http.get(`${baseUrl}/accounts/${accountId}`, { headers });
  check(account, {
    'account query returns 200': (response) => response.status === 200,
    'account query has rehydrated balance': (response) => response.json('balanceCents') === '1000',
    'account query has projected balance': (response) => response.json('projectionBalanceCents') === '1000',
  });

  const events = http.get(`${baseUrl}/accounts/${accountId}/events`, { headers });
  check(events, {
    'events query returns 200': (response) => response.status === 200,
    'events query has AccountOpened and MoneyDeposited': (response) => {
      const body = response.json();
      return Array.isArray(body) && body.length === 2 && body[0].type === 'AccountOpened' && body[1].type === 'MoneyDeposited';
    },
  });

  sleep(0.1);
}
