export interface DomainMessage<TPayload extends object = Record<string, unknown>> {
  messageId: string;
  domainId: string;
  domainType: 'transaction' | 'account' | 'ledger-transfer';
  eventType: string;
  checksumAlgorithm: 'md5';
  checksum: string;
  occurredAt: string;
  correlationId: string;
  payload: TPayload;
}
