const port = Number(process.env.TEST_PORT ?? process.env.PORT ?? '43102');
const baseUrl = process.env.BASE_URL ?? `http://127.0.0.1:${port}`;
const ownsServer = !process.env.BASE_URL;
let server: ReturnType<typeof Bun.spawn> | undefined;

if (ownsServer) {
  server = Bun.spawn(['bun', 'run', 'src/main.ts'], {
    stdout: 'pipe',
    stderr: 'pipe',
    env: { ...process.env, PORT: String(port), TIGERBEETLE_ENABLED: 'false', EDA_WORKER_ENABLED: 'false', OTEL_SDK_DISABLED: 'true' },
  });
}

async function main(): Promise<void> {
  try {
    await waitUntilReady(`${baseUrl}/healthz`);
    const accountId = `smoke-account-${Date.now()}`;
    await ensureOk(fetch(`${baseUrl}/accounts/${accountId}/open`, { method: 'POST', headers: { 'x-correlation-id': 'corr-smoke-open' } }));
    await ensureOk(
      fetch(`${baseUrl}/accounts/${accountId}/deposit`, {
        method: 'POST',
        headers: { 'content-type': 'application/json', 'x-correlation-id': 'corr-smoke-deposit' },
        body: JSON.stringify({ amount: 10, currency: 'ARS' }),
      }),
    );
    const account = await eventuallyJson(`/accounts/${accountId}`);
    if (account.balanceCents !== '1000') throw new Error(`expected 1000 cents, got ${account.balanceCents}`);
    console.log(`OK smoke ${baseUrl} account=${account.accountId} balanceCents=${account.balanceCents}`);
  } finally {
    server?.kill();
  }
}

void main();

async function ensureOk(responsePromise: Promise<Response>): Promise<void> {
  const response = await responsePromise;
  if (!response.ok) throw new Error(`HTTP ${response.status}: ${await response.text()}`);
}

async function eventuallyJson(path: string): Promise<any> {
  const deadline = Date.now() + 5_000;
  let lastBody: any;
  while (Date.now() < deadline) {
    const response = await fetch(`${baseUrl}${path}`);
    if (response.ok) {
      lastBody = await response.json();
      return lastBody;
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
