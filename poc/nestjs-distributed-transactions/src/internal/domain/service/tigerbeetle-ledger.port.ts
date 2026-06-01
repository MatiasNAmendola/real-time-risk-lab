import { Money } from '../value-object/money';

export const TIGERBEETLE_LEDGER = Symbol('TIGERBEETLE_LEDGER');

export interface LedgerTransferRequest {
  transferId: string;
  debitAccountId: string;
  creditAccountId: string;
  money: Money;
  correlationId: string;
  idempotencyKey: string;
}

export interface LedgerTransferResult {
  transferId: string;
  ledger: 'tigerbeetle' | 'in-memory-fallback';
  debitAccountId: string;
  creditAccountId: string;
  amountCents: string;
  reversible: boolean;
}

export interface TigerBeetleLedger {
  postTransfer(request: LedgerTransferRequest): Promise<LedgerTransferResult>;
  reverseTransfer(result: LedgerTransferResult, correlationId: string): Promise<LedgerTransferResult>;
}
