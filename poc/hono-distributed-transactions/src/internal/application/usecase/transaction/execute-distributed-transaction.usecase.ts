import { randomUUID } from 'crypto';
import { ExecuteSagaInput } from '../../dto/execute-saga.input';
import { SagaResponseDto } from '../../dto/saga-response.dto';
import { SagaStepName, TransactionSaga } from '../../../domain/entity/saga.entity';
import type { EventStoreRepository } from '../../../domain/repository/event-store.repository';
import type { SagaRepository } from '../../../domain/repository/saga.repository';
import type { TigerBeetleLedger, LedgerTransferResult } from '../../../domain/service/tigerbeetle-ledger.port';
import type { DomainEventPublisher } from '../../../domain/service/domain-event-publisher.port';
import { Money } from '../../../domain/value-object/money';

class SagaStepError extends Error {
  constructor(
    message: string,
    readonly failedStep: SagaStepName,
  ) {
    super(message);
  }
}

export class ExecuteDistributedTransactionUseCase {
  constructor(
    private readonly sagas: SagaRepository,
    private readonly events: EventStoreRepository,
    private readonly ledger: TigerBeetleLedger,
    private readonly publisher: DomainEventPublisher,
  ) {}

  async execute(input: ExecuteSagaInput, correlationId: string): Promise<SagaResponseDto> {
    const saga = new TransactionSaga(randomUUID(), input.transactionId, correlationId);
    let ledgerTransfer: LedgerTransferResult | undefined;
    let compensationTransfer: LedgerTransferResult | undefined;
    const money = Money.fromDecimal(input.amount, input.currency);

    try {
      await this.reserveInventory(input, saga);
      if (input.scenario === 'FAIL_AFTER_INVENTORY') {
        throw new SagaStepError('simulated failure before ledger transfer', 'post-ledger-transfer');
      }

      ledgerTransfer = await this.ledger.postTransfer({
        transferId: randomUUID(),
        debitAccountId: input.debitAccountId,
        creditAccountId: input.creditAccountId,
        money,
        correlationId,
        idempotencyKey: input.transactionId,
      });
      saga.markStep('post-ledger-transfer', 'DONE');
      this.publisher.publishLedgerTransferPosted(ledgerTransfer, input.currency, correlationId);
      if (input.scenario === 'FAIL_AFTER_LEDGER') {
        throw new SagaStepError('simulated failure before downstream notification', 'notify-downstream');
      }

      await this.notifyDownstream(input, saga);

      saga.complete();
      await this.sagas.save(saga);
    } catch (error) {
      const reason = error instanceof Error ? error.message : 'unknown saga failure';
      const failedStep = error instanceof SagaStepError ? error.failedStep : undefined;
      saga.compensate();

      if (failedStep && saga.stepStatus(failedStep) === 'PENDING') {
        saga.markStep(failedStep, 'FAILED', reason);
      }

      if (ledgerTransfer && saga.isStepDone('post-ledger-transfer')) {
        compensationTransfer = await this.ledger.reverseTransfer(ledgerTransfer, correlationId);
        saga.markStep('post-ledger-transfer', 'COMPENSATED', reason);
      }

      if (saga.isStepDone('reserve-inventory')) {
        saga.markStep('reserve-inventory', 'COMPENSATED', reason);
      }

      saga.markPendingAsSkipped(reason);
      saga.compensated();
      await this.sagas.save(saga);
    }

    return {
      saga,
      ledgerTransfer,
      compensationTransfer,
      events: await this.events.stream(input.transactionId),
    };
  }

  private async reserveInventory(input: ExecuteSagaInput, saga: TransactionSaga): Promise<void> {
    void input;
    saga.markStep('reserve-inventory', 'DONE');
  }

  private async notifyDownstream(input: ExecuteSagaInput, saga: TransactionSaga): Promise<void> {
    if (input.scenario === 'FAIL_NOTIFICATION') {
      throw new SagaStepError('simulated notification failure', 'notify-downstream');
    }
    saga.markStep('notify-downstream', 'DONE');
  }
}
