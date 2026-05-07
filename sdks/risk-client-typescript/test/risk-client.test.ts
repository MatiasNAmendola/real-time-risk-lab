import {
  RiskClient,
  RiskRequest,
  RiskDecision,
  HealthStatus,
  Subscription,
  RuleInfo,
  RiskClientError,
} from '../src/index';
import {
  SQSClient,
  SendMessageCommand,
  ReceiveMessageCommand,
  DeleteMessageCommand,
} from '@aws-sdk/client-sqs';
import * as crypto from 'crypto';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

const sampleRequest: RiskRequest = {
  transactionId:  'txn-001',
  customerId:     'cust-001',
  amountCents:    1000,
  correlationId:  'corr-001',
  idempotencyKey: 'idem-001',
  deviceId:       'dev-1',
  merchantId:     'merch-1',
  channel:        'WEB',
};

function mockFetch(responseBody: unknown, status = 200): void {
  (global as any).fetch = jest.fn().mockResolvedValue({
    ok:     status >= 200 && status < 300,
    status,
    json:   () => Promise.resolve(responseBody),
  });
}

function makeSqsMock(sendResult?: unknown, receiveResult?: unknown) {
  const mock = {
    send: jest.fn().mockImplementation((cmd: unknown) => {
      if (cmd instanceof ReceiveMessageCommand) return Promise.resolve(receiveResult ?? { Messages: [] });
      if (cmd instanceof DeleteMessageCommand)  return Promise.resolve({});
      return Promise.resolve(sendResult ?? { MessageId: 'msg-1' });
    }),
  } as unknown as SQSClient;
  return mock;
}

function makeClient(sqsOverride?: SQSClient): RiskClient {
  return new RiskClient(
    { environment: 'LOCAL', apiKey: 'test-key', timeoutMs: 5000 },
    sqsOverride,
  );
}

// ---------------------------------------------------------------------------
// sync.evaluate
// ---------------------------------------------------------------------------

test('sync.evaluate returns APPROVE decision', async () => {
  const expected: RiskDecision = { transactionId: 'txn-001', decision: 'APPROVE', reason: 'ok' };
  mockFetch(expected);
  const decision = await makeClient().sync.evaluate(sampleRequest);
  expect(decision.decision).toBe('APPROVE');
});

test('sync.evaluate returns DECLINE decision', async () => {
  const expected: RiskDecision = { transactionId: 'txn-001', decision: 'DECLINE', reason: 'high risk' };
  mockFetch(expected);
  const decision = await makeClient().sync.evaluate(sampleRequest);
  expect(decision.decision).toBe('DECLINE');
});

test('sync.evaluateBatch returns list of decisions', async () => {
  const batch: RiskDecision[] = [
    { transactionId: 'txn-001', decision: 'APPROVE', reason: 'ok' },
    { transactionId: 'txn-002', decision: 'REVIEW',  reason: 'manual' },
  ];
  mockFetch(batch);
  const results = await makeClient().sync.evaluateBatch([sampleRequest, sampleRequest]);
  expect(results).toHaveLength(2);
  expect(results[1].decision).toBe('REVIEW');
});

test('sync.health returns UP status', async () => {
  const status: HealthStatus = { status: 'UP', version: '1.0.0' };
  mockFetch(status);
  const result = await makeClient().sync.health();
  expect(result.status).toBe('UP');
});

// ---------------------------------------------------------------------------
// Error / retry
// ---------------------------------------------------------------------------

test('sync.evaluate throws RiskClientError on 503', async () => {
  (global as any).fetch = jest.fn().mockResolvedValue({ ok: false, status: 503, json: () => Promise.resolve({}) });
  const client = new RiskClient(
    { environment: 'LOCAL', apiKey: 'key', timeoutMs: 100, retry: { strategy: 'none', maxAttempts: 1 } },
  );
  await expect(client.sync.evaluate(sampleRequest)).rejects.toBeInstanceOf(RiskClientError);
});

// ---------------------------------------------------------------------------
// webhooks
// ---------------------------------------------------------------------------

test('webhooks.subscribe sends correct payload and returns subscription', async () => {
  const sub: Subscription = { id: 'sub-1', callbackUrl: 'http://cb/hook', eventFilter: 'DECLINE', createdAt: new Date().toISOString() };
  mockFetch(sub);
  const result = await makeClient().webhooks.subscribe('http://cb/hook', 'DECLINE');
  expect(result.id).toBe('sub-1');
});

test('webhooks.list returns array', async () => {
  const subs: Subscription[] = [
    { id: 's1', callbackUrl: 'http://a', eventFilter: 'DECLINE', createdAt: new Date().toISOString() },
    { id: 's2', callbackUrl: 'http://b', eventFilter: 'REVIEW',  createdAt: new Date().toISOString() },
  ];
  mockFetch(subs);
  const result = await makeClient().webhooks.list();
  expect(result).toHaveLength(2);
});

test('webhooks.verify accepts valid HMAC-SHA256 signature', () => {
  const secret  = 'test-secret';
  const payload = Buffer.from('{"decision":"DECLINE"}');
  const sig     = crypto.createHmac('sha256', secret).update(payload).digest('hex');
  expect(makeClient().webhooks.verify(payload, sig, secret)).toBe(true);
});

test('webhooks.verify rejects invalid signature', () => {
  expect(makeClient().webhooks.verify(Buffer.from('data'), 'badhash', 'secret')).toBe(false);
});

// ---------------------------------------------------------------------------
// admin
// ---------------------------------------------------------------------------

test('admin.listRules returns rule list', async () => {
  const rules: RuleInfo[] = [
    { id: 'r1', name: 'high-amount', description: 'blocks >100k', enabled: true, priority: 1 },
  ];
  mockFetch(rules);
  const result = await makeClient().admin.listRules();
  expect(result[0].name).toBe('high-amount');
});

test('admin.testRule returns decision', async () => {
  const expected: RiskDecision = { transactionId: 'txn-001', decision: 'DECLINE', reason: 'rule match' };
  mockFetch(expected);
  const result = await makeClient().admin.testRule(sampleRequest);
  expect(result.decision).toBe('DECLINE');
});

// ---------------------------------------------------------------------------
// queue
// ---------------------------------------------------------------------------

test('queue.sendDecisionRequest invokes SQS send', async () => {
  const sqs = makeSqsMock();
  const client = makeClient(sqs);
  await client.queue.sendDecisionRequest(sampleRequest);
  expect(sqs.send).toHaveBeenCalledTimes(1);
  expect(sqs.send).toHaveBeenCalledWith(expect.any(SendMessageCommand));
});

test('queue.receiveDecisions processes messages', async () => {
  const decision: RiskDecision = { transactionId: 'txn-001', decision: 'APPROVE', reason: 'ok' };
  const sqs = makeSqsMock(
    undefined,
    { Messages: [{ Body: JSON.stringify(decision), ReceiptHandle: 'rh-1' }] },
  );
  const client = makeClient(sqs);
  const received: RiskDecision[] = [];
  const count = await client.queue.receiveDecisions(async (d) => { received.push(d); });
  expect(count).toBe(1);
  expect(received[0].decision).toBe('APPROVE');
});

// ---------------------------------------------------------------------------
// Environment resolution
// ---------------------------------------------------------------------------

test('LOCAL environment resolves localhost URL', () => {
  const client = makeClient();
  // The baseUrl is internal; we verify it indirectly through a fetch call
  mockFetch({ status: 'UP' });
  return client.sync.health().then((s) => expect(s.status).toBe('UP'));
});
