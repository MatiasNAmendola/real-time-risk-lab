import { describe, expect, it } from 'bun:test';
import { TransactionSaga } from '@domain/entity/saga.entity';

describe('TransactionSaga', () => {
  it('marca pendientes como SKIPPED durante una compensación', () => {
    const saga = new TransactionSaga('saga-1', 'tx-1', 'corr-1');
    saga.markStep('reserve-inventory', 'DONE');
    saga.compensate();
    saga.markStep('reserve-inventory', 'COMPENSATED', 'falló ledger');
    saga.markPendingAsSkipped('falló ledger');
    saga.compensated();

    expect(saga.status).toBe('COMPENSATED');
    expect(saga.stepStatus('reserve-inventory')).toBe('COMPENSATED');
    expect(saga.stepStatus('post-ledger-transfer')).toBe('SKIPPED');
    expect(saga.stepStatus('notify-downstream')).toBe('SKIPPED');
  });
});
