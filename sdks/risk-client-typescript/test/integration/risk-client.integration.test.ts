/**
 * Integration tests for @naranjax/risk-client (TypeScript SDK).
 *
 * Assumes the full Vertx distributed stack is already running on localhost:8080.
 * Start the stack before running these tests:
 *
 *   docker compose -f poc/java-vertx-distributed/docker-compose.yml up -d --wait
 *
 * Run with: npm run test:integration
 *
 * The RISK_BASE_URL environment variable can override the default endpoint.
 */

import {
  RiskClient,
  RiskDecision,
  HealthStatus,
  Subscription,
  RuleInfo,
} from '../../src/index';
import { execSync } from 'child_process';

// ---------------------------------------------------------------------------
// Suite-level setup / teardown
// ---------------------------------------------------------------------------

const BASE_URL = process.env.RISK_BASE_URL ?? 'http://localhost:8080';
const COMPOSE_FILE =
  '../../poc/java-vertx-distributed/docker-compose.yml';

let client: RiskClient;
let dockerStarted = false;

beforeAll(async () => {
  if (!process.env.RISK_BASE_URL) {
    // Bring up the stack only when no external server is provided.
    try {
      execSync(
        `docker compose -f ${COMPOSE_FILE} up -d --wait`,
        { stdio: 'inherit', timeout: 180_000 },
      );
      dockerStarted = true;
    } catch {
      // If compose fails (e.g. already running), continue — tests may still pass.
      dockerStarted = false;
    }
  }

  client = new RiskClient({ environment: 'LOCAL', apiKey: 'test', timeoutMs: 10_000 });

  // Poll /healthz until the server is ready (up to 60 s).
  const deadline = Date.now() + 60_000;
  while (Date.now() < deadline) {
    try {
      const res = await fetch(`${BASE_URL}/healthz`);
      if (res.ok) break;
    } catch {
      // not ready yet
    }
    await new Promise((r) => setTimeout(r, 2_000));
  }
}, 120_000);

afterAll(() => {
  if (dockerStarted) {
    try {
      execSync(`docker compose -f ${COMPOSE_FILE} down`, { stdio: 'inherit' });
    } catch {
      // best-effort teardown
    }
  }
});

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function req(txId: string, amountCents: number) {
  return {
    transactionId:  txId,
    customerId:     'cust-1',
    amountCents,
    correlationId:  `corr-${txId}`,
    idempotencyKey: `idem-${txId}`,
    deviceId:       'known-dev-1',
    merchantId:     'merch-1',
    channel:        'WEB',
  };
}

// ---------------------------------------------------------------------------
// Tests — functional
// ---------------------------------------------------------------------------

it('evaluates low amount as APPROVE', async () => {
  const decision: RiskDecision = await client.sync.evaluate(req('tx-ts-1', 1.0));
  expect(decision.decision).toBe('APPROVE');
}, 15_000);

it('evaluates very high amount as DECLINE or REVIEW', async () => {
  const decision: RiskDecision = await client.sync.evaluate(req('tx-ts-2', 900_000.0));
  expect(['DECLINE', 'REVIEW']).toContain(decision.decision);
}, 15_000);

it('evaluateBatch returns one decision per request', async () => {
  const decisions = await client.sync.evaluateBatch([
    req('tx-ts-b1', 1.0),
    req('tx-ts-b2', 2.0),
    req('tx-ts-b3', 3.0),
  ]);
  expect(decisions).toHaveLength(3);
  decisions.forEach((d) => {
    expect(['APPROVE', 'DECLINE', 'REVIEW']).toContain(d.decision);
  });
}, 15_000);

it('idempotency: same transactionId returns same decision', async () => {
  const txId = `tx-ts-idem-${Date.now()}`;
  const first  = await client.sync.evaluate(req(txId, 1.0));
  const second = await client.sync.evaluate(req(txId, 1.0));
  expect(second.decision).toBe(first.decision);
  expect(second.reason).toBe(first.reason);
}, 15_000);

// ---------------------------------------------------------------------------
// Tests — health
// ---------------------------------------------------------------------------

it('health endpoint returns UP', async () => {
  const status: HealthStatus = await client.sync.health();
  expect(status.status).toBe('UP');
}, 10_000);

// ---------------------------------------------------------------------------
// Tests — webhooks
// ---------------------------------------------------------------------------

it('webhook subscribe returns subscription with id', async () => {
  const sub: Subscription = await client.webhooks.subscribe(
    'http://localhost:9999/cb-ts',
    'DECLINE',
  );
  expect(sub.id).toBeTruthy();
  expect(sub.callbackUrl).toBe('http://localhost:9999/cb-ts');
  expect(sub.eventFilter).toBe('DECLINE');
}, 10_000);

it('webhook list includes previously registered subscription', async () => {
  const url = `http://localhost:9997/cb-ts-list-${Date.now()}`;
  await client.webhooks.subscribe(url, 'REVIEW');
  const list: Subscription[] = await client.webhooks.list();
  expect(list.length).toBeGreaterThan(0);
  const found = list.some((s) => s.callbackUrl === url);
  expect(found).toBe(true);
}, 10_000);

// ---------------------------------------------------------------------------
// Tests — admin
// ---------------------------------------------------------------------------

it('admin lists at least one enabled rule', async () => {
  const rules: RuleInfo[] = await client.admin.listRules();
  expect(rules.length).toBeGreaterThan(0);
  rules.forEach((r) => {
    expect(r.id).toBeTruthy();
    expect(r.name).toBeTruthy();
  });
  const enabled = rules.filter((r) => r.enabled);
  expect(enabled.length).toBeGreaterThan(0);
}, 10_000);

it('admin testRule returns a valid decision', async () => {
  const decision: RiskDecision = await client.admin.testRule(req('tx-ts-admin-1', 1.0));
  expect(['APPROVE', 'DECLINE', 'REVIEW']).toContain(decision.decision);
  expect(decision.reason).toBeTruthy();
}, 10_000);
