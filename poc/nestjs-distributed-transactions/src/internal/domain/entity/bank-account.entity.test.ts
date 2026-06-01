import { describe, expect, it } from 'bun:test';
import { BankAccount } from '@domain/entity/bank-account.entity';

describe('BankAccount', () => {
  it('rehidrata el balance desde eventos', () => {
    const account = BankAccount.rehydrate('account-1', [
      { eventId: 'evt-1', aggregateId: 'account-1', type: 'AccountOpened', version: 1, occurredAt: '2026-01-01T00:00:00.000Z', correlationId: 'corr-1', payload: { currency: 'ARS' } },
      { eventId: 'evt-2', aggregateId: 'account-1', type: 'MoneyDeposited', version: 1, occurredAt: '2026-01-01T00:00:01.000Z', correlationId: 'corr-2', payload: { amountCents: '1000', currency: 'ARS' } },
      { eventId: 'evt-3', aggregateId: 'account-1', type: 'MoneyDeposited', version: 1, occurredAt: '2026-01-01T00:00:02.000Z', correlationId: 'corr-3', payload: { amountCents: '500', currency: 'ARS' } },
    ]);

    expect(account.snapshot()).toEqual({ accountId: 'account-1', balanceCents: '1500', currency: 'ARS', version: 3 });
  });
});
