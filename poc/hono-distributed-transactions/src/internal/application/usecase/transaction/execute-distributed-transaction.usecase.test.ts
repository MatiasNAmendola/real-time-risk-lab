import { describe, expect, it } from 'bun:test';
import { ExecuteDistributedTransactionUseCase } from '@application/usecase/transaction/execute-distributed-transaction.usecase';
import { TransactionSaga } from '@domain/entity/saga.entity';
import { DomainEvent } from '@domain/event/domain-event';
import { EventStoreRepository } from '@domain/repository/event-store.repository';
import { SagaRepository } from '@domain/repository/saga.repository';
import { DomainEventPublisher } from '@domain/service/domain-event-publisher.port';
import { LedgerTransferRequest, LedgerTransferResult, TigerBeetleLedger } from '@domain/service/tigerbeetle-ledger.port';

describe('ExecuteDistributedTransactionUseCase', () => {
  it('completes every saga step on success', async () => {
    const useCase = newUseCase();
    const response = await useCase.execute(baseInput('SUCCESS'), 'corr-success');
    expect(response.saga.status).toBe('COMPLETED');
    expect(response.saga.steps.every((step) => step.status === 'DONE')).toBe(true);
    expect(response.ledgerTransfer?.ledger).toBe('in-memory-fallback');
  });

  it('compensates only completed local transactions when the process fails before ledger', async () => {
    const useCase = newUseCase();
    const response = await useCase.execute(baseInput('FAIL_AFTER_INVENTORY'), 'corr-after-inventory');
    expect(response.saga.status).toBe('COMPENSATED');
    expect(response.saga.steps.find((step) => step.name === 'reserve-inventory')?.status).toBe('COMPENSATED');
    expect(response.saga.steps.find((step) => step.name === 'post-ledger-transfer')?.status).toBe('FAILED');
    expect(response.saga.steps.find((step) => step.name === 'notify-downstream')?.status).toBe('SKIPPED');
    expect(response.compensationTransfer).toBeUndefined();
  });

  it('compensates inventory and reverses ledger when the process fails after ledger', async () => {
    const useCase = newUseCase();
    const response = await useCase.execute(baseInput('FAIL_AFTER_LEDGER'), 'corr-rollback');
    expect(response.saga.status).toBe('COMPENSATED');
    expect(response.compensationTransfer?.debitAccountId).toBe('merchant-1');
    expect(response.compensationTransfer?.creditAccountId).toBe('payer-1');
    expect(response.saga.steps.find((step) => step.name === 'reserve-inventory')?.status).toBe('COMPENSATED');
    expect(response.saga.steps.find((step) => step.name === 'post-ledger-transfer')?.status).toBe('COMPENSATED');
    expect(response.saga.steps.find((step) => step.name === 'notify-downstream')?.status).toBe('FAILED');
  });

  it('marks notification as failed and compensates previous local transactions', async () => {
    const useCase = newUseCase();
    const response = await useCase.execute(baseInput('FAIL_NOTIFICATION'), 'corr-notification');
    expect(response.saga.status).toBe('COMPENSATED');
    expect(response.saga.steps.find((step) => step.name === 'reserve-inventory')?.status).toBe('COMPENSATED');
    expect(response.saga.steps.find((step) => step.name === 'post-ledger-transfer')?.status).toBe('COMPENSATED');
    expect(response.saga.steps.find((step) => step.name === 'notify-downstream')?.status).toBe('FAILED');
    expect(response.compensationTransfer).toBeDefined();
  });
});

function newUseCase(): ExecuteDistributedTransactionUseCase {
  return new ExecuteDistributedTransactionUseCase(
    new FakeSagaRepository(),
    new FakeEventStoreRepository(),
    new FakeLedger(),
    new NoopPublisher(),
  );
}

class FakeSagaRepository implements SagaRepository {
  private readonly sagas = new Map<string, TransactionSaga>();
  async save(saga: TransactionSaga): Promise<void> { this.sagas.set(saga.sagaId, saga); }
  async findById(sagaId: string): Promise<TransactionSaga | undefined> { return this.sagas.get(sagaId); }
  async list(): Promise<TransactionSaga[]> { return [...this.sagas.values()]; }
}

class FakeEventStoreRepository implements EventStoreRepository {
  async append(event: DomainEvent): Promise<void> { void event; }
  async stream(aggregateId: string): Promise<DomainEvent[]> { void aggregateId; return []; }
  async all(): Promise<DomainEvent[]> { return []; }
}

class FakeLedger implements TigerBeetleLedger {
  async postTransfer(request: LedgerTransferRequest): Promise<LedgerTransferResult> {
    return {
      transferId: request.transferId,
      ledger: 'in-memory-fallback',
      debitAccountId: request.debitAccountId,
      creditAccountId: request.creditAccountId,
      amountCents: request.money.cents.toString(),
      reversible: true,
    };
  }

  async reverseTransfer(result: LedgerTransferResult): Promise<LedgerTransferResult> {
    return {
      transferId: `${result.transferId}:reversal`,
      ledger: result.ledger,
      debitAccountId: result.creditAccountId,
      creditAccountId: result.debitAccountId,
      amountCents: result.amountCents,
      reversible: false,
    };
  }
}

class NoopPublisher implements DomainEventPublisher {
  publishLedgerTransferPosted(result: LedgerTransferResult, currency: string, correlationId: string): void {
    void result;
    void currency;
    void correlationId;
  }
}

function baseInput(scenario: 'SUCCESS' | 'FAIL_AFTER_INVENTORY' | 'FAIL_AFTER_LEDGER' | 'FAIL_NOTIFICATION') {
  return {
    transactionId: `tx-${scenario}`,
    debitAccountId: 'payer-1',
    creditAccountId: 'merchant-1',
    amount: 120.5,
    currency: 'ARS' as const,
    scenario,
  };
}
