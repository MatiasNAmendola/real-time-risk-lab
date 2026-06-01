import { DomainEvent } from '../event/domain-event';

export interface BankAccountState {
  accountId: string;
  balanceCents: string;
  currency: string;
  version: number;
}

export class BankAccount {
  private balance = 0n;
  private currency = 'ARS';
  private version = 0;

  constructor(public readonly accountId: string) {}

  static rehydrate(accountId: string, events: DomainEvent[]): BankAccount {
    const account = new BankAccount(accountId);
    for (const event of events) account.apply(event);
    return account;
  }

  apply(event: DomainEvent): void {
    if (event.type === 'AccountOpened') {
      const payload = event.payload as { currency?: string };
      this.currency = payload.currency ?? this.currency;
      this.version += 1;
      return;
    }

    if (event.type === 'MoneyDeposited') {
      const payload = event.payload as { amountCents: string; currency?: string };
      this.balance += BigInt(payload.amountCents);
      this.currency = payload.currency ?? this.currency;
      this.version += 1;
    }
  }

  snapshot(): BankAccountState {
    return {
      accountId: this.accountId,
      balanceCents: this.balance.toString(),
      currency: this.currency,
      version: this.version,
    };
  }
}
