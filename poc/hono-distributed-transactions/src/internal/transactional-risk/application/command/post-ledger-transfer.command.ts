export class PostLedgerTransferCommand {
  constructor(
    public readonly transferId: string,
    public readonly debitAccountId: string,
    public readonly creditAccountId: string,
    public readonly amountCents: string,
    public readonly currency: string,
    public readonly correlationId: string,
  ) {}
}
