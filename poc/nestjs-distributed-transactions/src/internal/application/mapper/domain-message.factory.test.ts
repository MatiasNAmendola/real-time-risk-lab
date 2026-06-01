import { describe, expect, it } from 'bun:test';
import { createDomainMessage, idempotencyKeyFor } from './domain-message.factory';

describe('domain-message.factory', () => {
  it('uses a stable MD5 checksum and domain ID for idempotency', () => {
    const first = createDomainMessage(
      {
        domainId: 'tx-eda-1',
        domainType: 'transaction',
        eventType: 'TransactionAccepted',
        payload: { amountCents: 1000, merchantId: 'm-1' },
      },
      'corr-1',
    );
    const second = createDomainMessage(
      {
        domainId: 'tx-eda-1',
        domainType: 'transaction',
        eventType: 'TransactionAccepted',
        payload: { merchantId: 'm-1', amountCents: 1000 },
      },
      'corr-2',
    );

    expect(first.checksumAlgorithm).toBe('md5');
    expect(first.checksum).toMatch(/^[a-f0-9]{32}$/);
    expect(first.checksum).toBe(second.checksum);
    expect(idempotencyKeyFor(first)).toBe(`transaction:tx-eda-1:${first.checksum}`);
  });
});
