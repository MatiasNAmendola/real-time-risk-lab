export interface AccountProjection {
  accountId: string;
  balanceCents: string;
  currency: string;
  lastEventId: string;
  updatedAt: string;
}

export const ACCOUNT_PROJECTION_REPOSITORY = Symbol('ACCOUNT_PROJECTION_REPOSITORY');
export interface AccountProjectionRepository {
  upsert(projection: AccountProjection): Promise<void>;
  findById(accountId: string): Promise<AccountProjection | undefined>;
  all(): Promise<AccountProjection[]>;
}
