import { serve } from '@hono/node-server';
import { Hono } from 'hono';
import { buildContainer } from './cmd/container';
import { transactionsRoutes } from './internal/infrastructure/controller/transactions.routes';

const container = buildContainer();
container.eda.startWorker();

const app = new Hono();

app.get('/healthz', (c) => c.json({ status: 'ok', app: 'hono-distributed-transactions' }));
app.route('/', transactionsRoutes(container));

app.onError((error, c) => {
  const correlationId = c.req.header('x-correlation-id') ?? crypto.randomUUID();
  c.header('X-Correlation-Id', correlationId);
  return c.json({ error: error.message, correlationId }, 422);
});

const port = Number(process.env.PORT ?? 3002);
serve({ fetch: app.fetch, port });
console.log(`Hono distributed transactions listening on http://localhost:${port}`);
