const assert = require('node:assert/strict');
const { spawn } = require('node:child_process');
const { randomUUID } = require('node:crypto');
const { BeforeAll, AfterAll, Given, When, Then } = require('@cucumber/cucumber');

const port = Number(process.env.TEST_PORT ?? '43104');
const baseUrl = process.env.BASE_URL ?? `http://127.0.0.1:${port}`;
const ownsServer = !process.env.BASE_URL;
let server;

BeforeAll({ timeout: 20_000 }, async function () {
  if (ownsServer) {
    server = spawn('bun', ['run', 'src/main.ts'], {
      cwd: process.cwd(),
      env: {
        ...process.env,
        PORT: String(port),
        TIGERBEETLE_ENABLED: 'false',
        EDA_WORKER_ENABLED: 'false',
        OTEL_SDK_DISABLED: 'true',
      },
      stdio: ['ignore', 'pipe', 'pipe'],
    });
  }
  await waitUntilReady(`${baseUrl}/healthz`);
});

AfterAll(async function () {
  if (server) {
    server.kill();
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
});

Given('el servicio de transacciones distribuidas está corriendo', async function () {
  const response = await fetch(`${baseUrl}/healthz`);
  assert.equal(response.status, 200);
});

When('abro una cuenta por HTTP', async function () {
  this.accountId = `account-cucumber-${randomUUID()}`;
  const response = await post(`/accounts/${this.accountId}/open`, undefined, 'corr-cucumber-open');
  await assertOk(response);
});

When('deposito dinero en la cuenta', async function () {
  const response = await post(`/accounts/${this.accountId}/deposit`, { amount: 10, currency: 'ARS' }, 'corr-cucumber-deposit');
  await assertOk(response);
});

When('deposito dinero dos veces', async function () {
  const first = await post(`/accounts/${this.accountId}/deposit`, { amount: 5, currency: 'ARS' }, 'corr-cucumber-deposit-1');
  await assertOk(first);
  const second = await post(`/accounts/${this.accountId}/deposit`, { amount: 7, currency: 'ARS' }, 'corr-cucumber-deposit-2');
  await assertOk(second);
});

When('publico una Saga con escenario FAIL_AFTER_LEDGER', async function () {
  const response = await post(
    '/transactions/sagas',
    {
      transactionId: `tx-cucumber-${randomUUID()}`,
      debitAccountId: 'payer-cucumber',
      creditAccountId: 'merchant-cucumber',
      amount: 42.25,
      currency: 'ARS',
      scenario: 'FAIL_AFTER_LEDGER',
    },
    'corr-cucumber-saga',
  );
  await assertOk(response);
  this.sagaResponse = await response.json();
});

Then('la consulta de balance devuelve el estado rehidratado desde eventos', async function () {
  const account = await eventuallyJson(`/accounts/${this.accountId}`, (body) => body.balanceCents === '1000');
  this.accountResponse = account;
  assert.equal(account.accountId, this.accountId);
  assert.equal(account.balanceCents, '1000');
  assert.equal(account.source, 'event-store+projection');
});

Then('la proyección de balance coincide con el estado rehidratado', function () {
  assert.equal(this.accountResponse.projectionBalanceCents, this.accountResponse.balanceCents);
});

Then('el Event Store expone AccountOpened y MoneyDeposited para esa cuenta', async function () {
  const events = await eventuallyJson(`/accounts/${this.accountId}/events`, (body) => Array.isArray(body) && body.length === 3);
  assert.deepEqual(
    events.map((event) => event.type),
    ['AccountOpened', 'MoneyDeposited', 'MoneyDeposited'],
  );
});

Then('la Saga finaliza con estado COMPENSATED', function () {
  assert.equal(this.sagaResponse.saga.status, 'COMPENSATED');
});

Then('existe una transferencia compensatoria', function () {
  assert.ok(this.sagaResponse.compensationTransfer);
});

async function assertOk(response) {
  if (response.status < 200 || response.status >= 300) {
    throw new Error(`HTTP ${response.status}: ${await response.text()}`);
  }
}

async function post(path, body, correlationId) {
  const headers = { 'X-Correlation-Id': correlationId };
  const init = { method: 'POST', headers };
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    init.body = JSON.stringify(body);
  }
  return fetch(`${baseUrl}${path}`, init);
}

async function eventuallyJson(path, predicate = () => true) {
  const deadline = Date.now() + 5_000;
  let lastBody;
  while (Date.now() < deadline) {
    const response = await fetch(`${baseUrl}${path}`);
    if (response.ok) {
      lastBody = await response.json();
      if (predicate(lastBody)) return lastBody;
    }
    await new Promise((resolve) => setTimeout(resolve, 50));
  }
  throw new Error(`condition not met for ${path}: ${JSON.stringify(lastBody)}`);
}

async function waitUntilReady(url) {
  const deadline = Date.now() + 15_000;
  let lastError;
  while (Date.now() < deadline) {
    try {
      const response = await fetch(url);
      if (response.ok) return;
    } catch (error) {
      lastError = error;
    }
    await new Promise((resolve) => setTimeout(resolve, 150));
  }
  throw new Error(`service not ready: ${url}; ${lastError ? lastError.message : 'no response'}`);
}
