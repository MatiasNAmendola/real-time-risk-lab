import { afterAll, beforeAll, describe, expect, it } from 'bun:test';

const port = Number(process.env.TEST_PORT ?? '43101');
const baseUrl = `http://127.0.0.1:${port}`;
let server: ReturnType<typeof Bun.spawn> | undefined;

beforeAll(async () => {
  server = Bun.spawn(['bun', 'run', 'src/main.ts'], {
    stdout: 'pipe',
    stderr: 'pipe',
    env: {
      ...process.env,
      PORT: String(port),
      TIGERBEETLE_ENABLED: 'false',
      EDA_WORKER_ENABLED: 'false',
      OTEL_SDK_DISABLED: 'true',
    },
  });
  await waitUntilReady(`${baseUrl}/healthz`);
}, 20_000);

afterAll(() => {
  server?.kill();
});

describe('ATDD HTTP - cuenta bancaria simple', () => {
  it('abre una cuenta, deposita dinero y lee balance rehidratado/proyectado', async () => {
    const accountId = `account-${Date.now()}`;

    expectOk(await post(`/accounts/${accountId}/open`, {}, 'corr-simple-open'));
    expectOk(await post(`/accounts/${accountId}/deposit`, { amount: 10.5, currency: 'ARS' }, 'corr-simple-deposit'));

    const account = await eventuallyJson(`/accounts/${accountId}`);
    expect(account.accountId).toBe(accountId);
    expect(account.balanceCents).toBe('1050');
    expect(account.projectionBalanceCents).toBe('1050');
    expect(account.source).toBe('event-store+projection');
  });

  it('expone el Event Store append-only de la cuenta', async () => {
    const accountId = `account-events-${Date.now()}`;

    expectOk(await post(`/accounts/${accountId}/open`, {}, 'corr-events-open'));
    expectOk(await post(`/accounts/${accountId}/deposit`, { amount: 5, currency: 'ARS' }, 'corr-events-deposit-1'));
    expectOk(await post(`/accounts/${accountId}/deposit`, { amount: 7.25, currency: 'ARS' }, 'corr-events-deposit-2'));

    const events = await eventuallyJson(`/accounts/${accountId}/events`, (body) => Array.isArray(body) && body.length === 3);
    expect(events.map((event: { type: string }) => event.type)).toEqual(['AccountOpened', 'MoneyDeposited', 'MoneyDeposited']);
  });
});

describe('ATDD HTTP - demo avanzada separada', () => {
  it('compensa una Saga cuando falla después del ledger', async () => {
    const response = await post('/transactions/sagas', sagaPayload('FAIL_AFTER_LEDGER'), 'corr-atdd-compensation');
    expectOk(response);
    const body = await response.json();
    expect(body.saga.status).toBe('COMPENSATED');
    expect(body.compensationTransfer).toBeDefined();
    expect(body.saga.steps.find((step: { name: string }) => step.name === 'post-ledger-transfer')?.status).toBe('COMPENSATED');
  });
});

function expectOk(response: Response): void {
  expect(response.status).toBeGreaterThanOrEqual(200);
  expect(response.status).toBeLessThan(300);
}

function sagaPayload(scenario: 'SUCCESS' | 'FAIL_AFTER_LEDGER') {
  return {
    transactionId: `tx-${scenario.toLowerCase()}-${Date.now()}`,
    debitAccountId: 'payer-atdd',
    creditAccountId: 'merchant-atdd',
    amount: 42.25,
    currency: 'ARS',
    scenario,
  };
}

async function post(path: string, body: unknown, correlationId: string): Promise<Response> {
  return fetch(`${baseUrl}${path}`, {
    method: 'POST',
    headers: { 'content-type': 'application/json', 'x-correlation-id': correlationId },
    body: JSON.stringify(body),
  });
}

async function eventuallyJson(path: string, predicate: (body: any) => boolean = () => true): Promise<any> {
  const deadline = Date.now() + 5_000;
  let lastBody: any;
  while (Date.now() < deadline) {
    const response = await fetch(`${baseUrl}${path}`);
    if (response.ok) {
      lastBody = await response.json();
      if (predicate(lastBody)) return lastBody;
    }
    await Bun.sleep(50);
  }
  throw new Error(`condition not met for ${path}: ${JSON.stringify(lastBody)}`);
}

async function waitUntilReady(url: string): Promise<void> {
  const deadline = Date.now() + 15_000;
  while (Date.now() < deadline) {
    try {
      const response = await fetch(url);
      if (response.ok) return;
    } catch {
      // retry until server binds the port
    }
    await Bun.sleep(150);
  }
  throw new Error(`service not ready: ${url}`);
}
