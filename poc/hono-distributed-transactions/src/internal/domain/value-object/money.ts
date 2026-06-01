export type Currency = 'ARS' | 'USD';

export class Money {
  private constructor(
    public readonly cents: bigint,
    public readonly currency: Currency,
  ) {}

  static fromDecimal(amount: number, currency: Currency): Money {
    if (!Number.isFinite(amount) || amount <= 0) throw new Error('amount must be positive');
    return new Money(BigInt(Math.round(amount * 100)), currency);
  }

  toJSON(): { cents: string; currency: Currency } {
    return { cents: this.cents.toString(), currency: this.currency };
  }
}
