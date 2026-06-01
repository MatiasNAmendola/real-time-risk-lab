export class AccountOpenedEvent {
  constructor(
    public readonly accountId: string,
    public readonly currency: string,
    public readonly correlationId: string,
  ) {}
}

export class LedgerTransferPostedEvent {
  constructor(
    public readonly transferId: string,
    public readonly debitAccountId: string,
    public readonly creditAccountId: string,
    public readonly amountCents: string,
    public readonly currency: string,
    public readonly correlationId: string,
  ) {}
}


export class MoneyDepositedEvent {
  constructor(
    public readonly accountId: string,
    public readonly amountCents: string,
    public readonly currency: string,
    public readonly correlationId: string,
  ) {}
}
