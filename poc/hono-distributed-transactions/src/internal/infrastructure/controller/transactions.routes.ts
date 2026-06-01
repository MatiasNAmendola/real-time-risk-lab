import { randomUUID } from 'crypto';
import { Hono } from 'hono';
import { z } from 'zod';
import { DepositMoneyCommand } from '../../application/command/deposit-money.command';
import { OpenAccountCommand } from '../../application/command/open-account.command';
import { PostLedgerTransferCommand } from '../../application/command/post-ledger-transfer.command';
import { GetAccountBalanceQuery } from '../../application/query/get-account-balance.query';
import { AppContainer } from '../../../cmd/container';

const depositMoneySchema = z.object({
  amount: z.number().positive(),
  currency: z.enum(['ARS', 'USD']).optional(),
});

const executeSagaSchema = z.object({
  transactionId: z.string().min(3),
  debitAccountId: z.string().min(1),
  creditAccountId: z.string().min(1),
  amount: z.number().positive(),
  currency: z.enum(['ARS', 'USD']),
  scenario: z.enum(['SUCCESS', 'FAIL_AFTER_INVENTORY', 'FAIL_AFTER_LEDGER', 'FAIL_NOTIFICATION']).optional(),
});

const edaMessageSchema = z.object({
  domainId: z.string().min(3),
  domainType: z.enum(['transaction', 'account', 'ledger-transfer']),
  eventType: z.string().min(3),
  payload: z.record(z.string(), z.unknown()).optional(),
});

export function transactionsRoutes(container: AppContainer): Hono {
  const app = new Hono();

  app.post('/accounts/:id/open', async (c) => {
    return c.json(await container.commandBus.openAccount(new OpenAccountCommand(c.req.param('id'), 'ARS', correlationId(c))));
  });

  app.post('/accounts/:id/deposit', async (c) => {
    const body = depositMoneySchema.parse(await c.req.json());
    return c.json(
      await container.commandBus.depositMoney(
        new DepositMoneyCommand(c.req.param('id'), BigInt(Math.round(body.amount * 100)).toString(), body.currency ?? 'ARS', correlationId(c)),
      ),
    );
  });

  app.get('/accounts/:id', async (c) => {
    const account = await container.queryBus.getAccountBalance(new GetAccountBalanceQuery(c.req.param('id')));
    return account ? c.json(account) : c.json({ error: 'account not found' }, 404);
  });

  app.get('/accounts/:id/events', async (c) => c.json(await container.events.stream(c.req.param('id'))));

  app.post('/transactions/sagas', async (c) => {
    const body = executeSagaSchema.parse(await c.req.json());
    return c.json(await container.executeSaga.execute(body, correlationId(c)));
  });

  app.get('/transactions/sagas', async (c) => c.json(await container.sagas.list()));

  app.get('/transactions/sagas/:id', async (c) => {
    const saga = await container.sagas.findById(c.req.param('id'));
    return saga ? c.json(saga) : c.json({ error: 'saga not found' }, 404);
  });

  app.post('/transactions/cqrs/accounts/:id/open', async (c) => {
    return c.json(await container.commandBus.openAccount(new OpenAccountCommand(c.req.param('id'), 'ARS', correlationId(c))));
  });

  app.post('/transactions/cqrs/transfers', async (c) => {
    const body = executeSagaSchema.parse(await c.req.json());
    return c.json(
      await container.commandBus.postLedgerTransfer(
        new PostLedgerTransferCommand(
          randomUUID(),
          body.debitAccountId,
          body.creditAccountId,
          BigInt(Math.round(body.amount * 100)).toString(),
          body.currency,
          correlationId(c),
        ),
      ),
    );
  });

  app.get('/transactions/cqrs/accounts', async (c) => c.json(await container.projections.all()));

  app.get('/transactions/cqrs/accounts/:id/projection', async (c) => {
    const projection = await container.projections.findById(c.req.param('id'));
    return projection ? c.json(projection) : c.json({ error: 'projection not found' }, 404);
  });

  app.post('/transactions/eda/messages', async (c) => {
    const body = edaMessageSchema.parse(await c.req.json());
    return c.json(await container.eda.publish(body, correlationId(c)));
  });

  app.post('/transactions/eda/jobs/:jobId/process', async (c) => c.json(await container.eda.processById(c.req.param('jobId'))));

  app.get('/transactions/events', async (c) => c.json(await container.events.all()));
  app.get('/transactions/events/:aggregateId', async (c) => c.json(await container.events.stream(c.req.param('aggregateId'))));

  return app;
}

function correlationId(c: { req: { header(name: string): string | undefined }; header(name: string, value: string): void }): string {
  const incoming = c.req.header('x-correlation-id');
  const id = incoming && incoming.length > 0 ? incoming : randomUUID();
  c.header('X-Correlation-Id', id);
  return id;
}
