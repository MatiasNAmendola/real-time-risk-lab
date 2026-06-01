export interface IdempotencyRecord {
  idempotencyKey: string;
  domainId: string;
  checksum: string;
  firstSeenAt: string;
}

export interface IdempotencyResult {
  accepted: boolean;
  reason: 'FIRST_SEEN' | 'DUPLICATE_SAME_CHECKSUM' | 'DUPLICATE_CHECKSUM_MISMATCH';
  record: IdempotencyRecord;
}

export const MESSAGE_IDEMPOTENCY_REPOSITORY = Symbol('MESSAGE_IDEMPOTENCY_REPOSITORY');

export interface MessageIdempotencyRepository {
  markFirstSeen(record: IdempotencyRecord): Promise<IdempotencyResult>;
  find(idempotencyKey: string): Promise<IdempotencyRecord | undefined>;
}
