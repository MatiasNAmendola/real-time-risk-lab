import { LedgerTransferResult } from './tigerbeetle-ledger.port';

export interface DomainEventPublisher {
  publishLedgerTransferPosted(result: LedgerTransferResult, currency: string, correlationId: string): void;
}
