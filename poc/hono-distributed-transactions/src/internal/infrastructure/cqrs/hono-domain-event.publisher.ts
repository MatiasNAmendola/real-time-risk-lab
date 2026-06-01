import { LedgerTransferPostedEvent } from '../../application/event/account-events';
import { DomainEventPublisher } from '../../domain/service/domain-event-publisher.port';
import { LedgerTransferResult } from '../../domain/service/tigerbeetle-ledger.port';
import { ProjectionEventHandlers } from './projection-event-handlers';

export class HonoDomainEventPublisher implements DomainEventPublisher {
  constructor(private readonly handlers: ProjectionEventHandlers) {}

  publishLedgerTransferPosted(result: LedgerTransferResult, currency: string, correlationId: string): void {
    void this.handlers.ledgerTransferPosted(
      new LedgerTransferPostedEvent(
        result.transferId,
        result.debitAccountId,
        result.creditAccountId,
        result.amountCents,
        currency,
        correlationId,
      ),
    );
  }
}
