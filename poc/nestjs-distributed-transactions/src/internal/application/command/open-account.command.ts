export class OpenAccountCommand {
  constructor(
    public readonly accountId: string,
    public readonly currency: string,
    public readonly correlationId: string,
  ) {}
}
