import { Injectable } from '@nestjs/common';
import { TransactionSaga } from '@domain/entity/saga.entity';
import { SagaRepository } from '@domain/repository/saga.repository';

@Injectable()
export class InMemorySagaRepository implements SagaRepository {
  private readonly sagas = new Map<string, TransactionSaga>();
  async save(saga: TransactionSaga): Promise<void> { this.sagas.set(saga.sagaId, saga); }
  async findById(sagaId: string): Promise<TransactionSaga | undefined> { return this.sagas.get(sagaId); }
  async list(): Promise<TransactionSaga[]> { return [...this.sagas.values()]; }
}
