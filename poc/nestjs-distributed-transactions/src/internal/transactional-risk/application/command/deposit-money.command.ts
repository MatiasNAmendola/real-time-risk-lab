export class DepositMoneyCommand {
  constructor(
    public readonly accountId: string,
    public readonly amountCents: string,
    public readonly currency: string,
    public readonly correlationId: string,
  ) {}
}
