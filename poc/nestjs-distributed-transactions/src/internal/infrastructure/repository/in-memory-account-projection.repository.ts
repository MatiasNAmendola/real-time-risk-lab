import { Injectable } from '@nestjs/common';
import { AccountProjection, AccountProjectionRepository } from '../../domain/repository/account-projection.repository';

@Injectable()
export class InMemoryAccountProjectionRepository implements AccountProjectionRepository {
  private readonly projections = new Map<string, AccountProjection>();
  async upsert(projection: AccountProjection): Promise<void> { this.projections.set(projection.accountId, projection); }
  async findById(accountId: string): Promise<AccountProjection | undefined> { return this.projections.get(accountId); }
  async all(): Promise<AccountProjection[]> { return [...this.projections.values()]; }
}
