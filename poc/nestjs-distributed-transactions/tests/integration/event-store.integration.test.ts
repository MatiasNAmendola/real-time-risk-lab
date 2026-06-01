import { describe, expect, it } from 'bun:test';
import { InMemoryEventStoreRepository } from '@infrastructure/repository/in-memory-event-store.repository';

describe('InMemoryEventStoreRepository', () => {
  it('mantiene streams por aggregateId y permite auditoría append-only', async () => {
    const repository = new InMemoryEventStoreRepository();
    await repository.append({ eventId: 'evt-1', aggregateId: 'account-1', type: 'AccountOpened', version: 1, payload: { currency: 'ARS' }, occurredAt: '2026-01-01T00:00:00.000Z', correlationId: 'corr-1' });
    await repository.append({ eventId: 'evt-2', aggregateId: 'account-2', type: 'AccountOpened', version: 1, payload: { currency: 'ARS' }, occurredAt: '2026-01-01T00:00:01.000Z', correlationId: 'corr-2' });
    await repository.append({ eventId: 'evt-3', aggregateId: 'account-1', type: 'LedgerTransferPosted', version: 1, payload: { amountCents: '1000' }, occurredAt: '2026-01-01T00:00:02.000Z', correlationId: 'corr-3' });

    expect(await repository.stream('account-1')).toHaveLength(2);
    expect(await repository.stream('account-2')).toHaveLength(1);
    expect(await repository.all()).toHaveLength(3);
  });
});
