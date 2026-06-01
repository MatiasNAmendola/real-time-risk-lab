export interface PublishEdaMessageInput {
  domainId: string;
  domainType: 'transaction' | 'account' | 'ledger-transfer';
  eventType: string;
  payload?: Record<string, unknown>;
}
