import { Inject } from '@nestjs/common';
import { EventsHandler, IEventHandler } from '@nestjs/cqrs';
import { randomUUID } from 'crypto';
import { AccountOpenedEvent, LedgerTransferPostedEvent, MoneyDepositedEvent } from '../../application/event/account-events';
import { ACCOUNT_PROJECTION_REPOSITORY } from '../../domain/repository/account-projection.repository';
import type { AccountProjectionRepository } from '../../domain/repository/account-projection.repository';
import { EVENT_STORE_REPOSITORY } from '../../domain/repository/event-store.repository';
import type { EventStoreRepository } from '../../domain/repository/event-store.repository';

@EventsHandler(AccountOpenedEvent)
export class AccountOpenedHandler implements IEventHandler<AccountOpenedEvent> {
  constructor(
    @Inject(EVENT_STORE_REPOSITORY) private readonly events: EventStoreRepository,
    @Inject(ACCOUNT_PROJECTION_REPOSITORY) private readonly projections: AccountProjectionRepository,
  ) {}

  async handle(event: AccountOpenedEvent): Promise<void> {
    const eventId = randomUUID();
    await this.events.append({
      eventId,
      aggregateId: event.accountId,
      type: 'AccountOpened',
      version: 1,
      occurredAt: new Date().toISOString(),
      correlationId: event.correlationId,
      payload: { accountId: event.accountId, currency: event.currency },
    });
    await this.projections.upsert({
      accountId: event.accountId,
      balanceCents: '0',
      currency: event.currency,
      lastEventId: eventId,
      updatedAt: new Date().toISOString(),
    });
  }
}

@EventsHandler(MoneyDepositedEvent)
export class MoneyDepositedHandler implements IEventHandler<MoneyDepositedEvent> {
  constructor(
    @Inject(EVENT_STORE_REPOSITORY) private readonly events: EventStoreRepository,
    @Inject(ACCOUNT_PROJECTION_REPOSITORY) private readonly projections: AccountProjectionRepository,
  ) {}

  async handle(event: MoneyDepositedEvent): Promise<void> {
    const eventId = randomUUID();
    const occurredAt = new Date().toISOString();
    await this.events.append({
      eventId,
      aggregateId: event.accountId,
      type: 'MoneyDeposited',
      version: 1,
      occurredAt,
      correlationId: event.correlationId,
      payload: { accountId: event.accountId, amountCents: event.amountCents, currency: event.currency },
    });
    await this.applyBalance(event.accountId, BigInt(event.amountCents), event.currency, eventId, occurredAt);
  }

  private async applyBalance(accountId: string, delta: bigint, currency: string, lastEventId: string, updatedAt: string): Promise<void> {
    const current = await this.projections.findById(accountId);
    await this.projections.upsert({
      accountId,
      balanceCents: ((current ? BigInt(current.balanceCents) : 0n) + delta).toString(),
      currency,
      lastEventId,
      updatedAt,
    });
  }
}

@EventsHandler(LedgerTransferPostedEvent)
export class LedgerTransferPostedHandler implements IEventHandler<LedgerTransferPostedEvent> {
  constructor(
    @Inject(EVENT_STORE_REPOSITORY) private readonly events: EventStoreRepository,
    @Inject(ACCOUNT_PROJECTION_REPOSITORY) private readonly projections: AccountProjectionRepository,
  ) {}

  async handle(event: LedgerTransferPostedEvent): Promise<void> {
    const eventId = randomUUID();
    const occurredAt = new Date().toISOString();
    await this.events.append({
      eventId,
      aggregateId: event.transferId,
      type: 'LedgerTransferPosted',
      version: 1,
      occurredAt,
      correlationId: event.correlationId,
      payload: { ...event },
    });
    await this.applyBalance(event.debitAccountId, -BigInt(event.amountCents), event.currency, eventId, occurredAt);
    await this.applyBalance(event.creditAccountId, BigInt(event.amountCents), event.currency, eventId, occurredAt);
  }

  private async applyBalance(accountId: string, delta: bigint, currency: string, lastEventId: string, updatedAt: string): Promise<void> {
    const current = await this.projections.findById(accountId);
    await this.projections.upsert({
      accountId,
      balanceCents: ((current ? BigInt(current.balanceCents) : 0n) + delta).toString(),
      currency,
      lastEventId,
      updatedAt,
    });
  }
}

export const EventSourcingHandlers = [AccountOpenedHandler, MoneyDepositedHandler, LedgerTransferPostedHandler];
