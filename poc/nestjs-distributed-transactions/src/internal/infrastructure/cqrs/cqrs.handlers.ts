import { Inject, NotFoundException } from '@nestjs/common';
import { CommandHandler, EventBus, ICommandHandler, IQueryHandler, QueryHandler } from '@nestjs/cqrs';
import { DepositMoneyCommand } from '@application/command/deposit-money.command';
import { OpenAccountCommand } from '@application/command/open-account.command';
import { PostLedgerTransferCommand } from '@application/command/post-ledger-transfer.command';
import { AccountOpenedEvent, LedgerTransferPostedEvent, MoneyDepositedEvent } from '@application/event/account-events';
import { GetAccountBalanceQuery } from '@application/query/get-account-balance.query';
import { BankAccount, BankAccountState } from '@domain/entity/bank-account.entity';
import { ACCOUNT_PROJECTION_REPOSITORY } from '@domain/repository/account-projection.repository';
import type { AccountProjectionRepository } from '@domain/repository/account-projection.repository';
import { EVENT_STORE_REPOSITORY } from '@domain/repository/event-store.repository';
import type { EventStoreRepository } from '@domain/repository/event-store.repository';

export interface AccountBalanceView extends BankAccountState {
  projectionBalanceCents: string;
  source: 'event-store+projection';
}

@CommandHandler(OpenAccountCommand)
export class OpenAccountHandler implements ICommandHandler<OpenAccountCommand> {
  constructor(private readonly eventBus: EventBus) {}

  async execute(command: OpenAccountCommand): Promise<{ accepted: true; accountId: string }> {
    this.eventBus.publish(new AccountOpenedEvent(command.accountId, command.currency, command.correlationId));
    return { accepted: true, accountId: command.accountId };
  }
}

@CommandHandler(DepositMoneyCommand)
export class DepositMoneyHandler implements ICommandHandler<DepositMoneyCommand> {
  constructor(private readonly eventBus: EventBus) {}

  async execute(command: DepositMoneyCommand): Promise<{ accepted: true; accountId: string; amountCents: string }> {
    this.eventBus.publish(new MoneyDepositedEvent(command.accountId, command.amountCents, command.currency, command.correlationId));
    return { accepted: true, accountId: command.accountId, amountCents: command.amountCents };
  }
}

@CommandHandler(PostLedgerTransferCommand)
export class PostLedgerTransferHandler implements ICommandHandler<PostLedgerTransferCommand> {
  constructor(private readonly eventBus: EventBus) {}

  async execute(command: PostLedgerTransferCommand): Promise<{ accepted: true; transferId: string }> {
    this.eventBus.publish(
      new LedgerTransferPostedEvent(
        command.transferId,
        command.debitAccountId,
        command.creditAccountId,
        command.amountCents,
        command.currency,
        command.correlationId,
      ),
    );
    return { accepted: true, transferId: command.transferId };
  }
}

@QueryHandler(GetAccountBalanceQuery)
export class GetAccountBalanceHandler implements IQueryHandler<GetAccountBalanceQuery> {
  constructor(
    @Inject(EVENT_STORE_REPOSITORY) private readonly events: EventStoreRepository,
    @Inject(ACCOUNT_PROJECTION_REPOSITORY) private readonly projections: AccountProjectionRepository,
  ) {}

  async execute(query: GetAccountBalanceQuery): Promise<AccountBalanceView> {
    const stream = await this.events.stream(query.accountId);
    if (stream.length === 0) throw new NotFoundException('account not found');
    const account = BankAccount.rehydrate(query.accountId, stream).snapshot();
    const projection = await this.projections.findById(query.accountId);
    return { ...account, projectionBalanceCents: projection?.balanceCents ?? '0', source: 'event-store+projection' };
  }
}

export const CommandHandlers = [OpenAccountHandler, DepositMoneyHandler, PostLedgerTransferHandler];
export const QueryHandlers = [GetAccountBalanceHandler];
