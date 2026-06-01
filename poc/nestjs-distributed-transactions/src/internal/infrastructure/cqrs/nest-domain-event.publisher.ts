import { Injectable } from '@nestjs/common';
import { EventBus } from '@nestjs/cqrs';
import { LedgerTransferPostedEvent } from '@application/event/account-events';
import { DomainEventPublisher } from '@domain/service/domain-event-publisher.port';
import { LedgerTransferResult } from '@domain/service/tigerbeetle-ledger.port';

@Injectable()
export class NestDomainEventPublisher implements DomainEventPublisher {
  constructor(private readonly eventBus: EventBus) {}

  publishLedgerTransferPosted(result: LedgerTransferResult, currency: string, correlationId: string): void {
    this.eventBus.publish(
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
