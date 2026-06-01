export interface ExecuteSagaInput {
  transactionId: string;
  debitAccountId: string;
  creditAccountId: string;
  amount: number;
  currency: 'ARS' | 'USD';
  scenario?: 'SUCCESS' | 'FAIL_AFTER_INVENTORY' | 'FAIL_AFTER_LEDGER' | 'FAIL_NOTIFICATION';
}
