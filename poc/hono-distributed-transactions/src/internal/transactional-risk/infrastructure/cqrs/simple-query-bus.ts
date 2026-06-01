import { GetAccountBalanceQuery } from '@application/query/get-account-balance.query';
import { BankAccount, BankAccountState } from '@domain/entity/bank-account.entity';
import { AccountProjectionRepository } from '@domain/repository/account-projection.repository';
import { EventStoreRepository } from '@domain/repository/event-store.repository';

export interface AccountBalanceView extends BankAccountState {
  projectionBalanceCents: string;
  source: 'event-store+projection';
}

export class SimpleQueryBus {
  constructor(
    private readonly events: EventStoreRepository,
    private readonly projections: AccountProjectionRepository,
  ) {}

  async getAccountBalance(query: GetAccountBalanceQuery): Promise<AccountBalanceView | undefined> {
    const stream = await this.events.stream(query.accountId);
    if (stream.length === 0) return undefined;
    const account = BankAccount.rehydrate(query.accountId, stream).snapshot();
    const projection = await this.projections.findById(query.accountId);
    return {
      ...account,
      projectionBalanceCents: projection?.balanceCents ?? '0',
      source: 'event-store+projection',
    };
  }
}
