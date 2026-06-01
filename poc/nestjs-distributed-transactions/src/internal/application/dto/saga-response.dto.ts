import { TransactionSaga } from '../../domain/entity/saga.entity';
import { LedgerTransferResult } from '../../domain/service/tigerbeetle-ledger.port';
import { DomainEvent } from '../../domain/event/domain-event';

export interface SagaResponseDto {
  saga: TransactionSaga;
  ledgerTransfer?: LedgerTransferResult;
  compensationTransfer?: LedgerTransferResult;
  events: DomainEvent[];
}
