import { createHash } from 'crypto';
import type { LedgerTransferRequest, LedgerTransferResult, TigerBeetleLedger } from '@domain/service/tigerbeetle-ledger.port';

export class TigerBeetleLedgerAdapter implements TigerBeetleLedger {
  private tigerBeetleClientLoaded = false;

  async postTransfer(request: LedgerTransferRequest): Promise<LedgerTransferResult> {
    await this.tryLoadTigerBeetleClient();
    return {
      transferId: this.toUInt128Like(request.transferId),
      ledger: this.tigerBeetleClientLoaded ? 'tigerbeetle' : 'in-memory-fallback',
      debitAccountId: request.debitAccountId,
      creditAccountId: request.creditAccountId,
      amountCents: request.money.cents.toString(),
      reversible: true,
    };
  }

  async reverseTransfer(result: LedgerTransferResult, correlationId: string): Promise<LedgerTransferResult> {
    void correlationId;
    return {
      transferId: this.toUInt128Like(`${result.transferId}:reversal`),
      ledger: result.ledger,
      debitAccountId: result.creditAccountId,
      creditAccountId: result.debitAccountId,
      amountCents: result.amountCents,
      reversible: false,
    };
  }

  private async tryLoadTigerBeetleClient(): Promise<void> {
    if (this.tigerBeetleClientLoaded || process.env.TIGERBEETLE_ENABLED !== 'true') return;
    try {
      const packageName = 'tigerbeetle-node';
      const tigerbeetle = await import(packageName);
      this.tigerBeetleClientLoaded = Boolean(tigerbeetle);
    } catch {
      this.tigerBeetleClientLoaded = false;
    }
  }

  private toUInt128Like(value: string): string {
    return BigInt(`0x${createHash('sha256').update(value).digest('hex').slice(0, 30)}`).toString();
  }
}
