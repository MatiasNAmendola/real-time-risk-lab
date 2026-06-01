import { TransactionSaga } from '../entity/saga.entity';

export const SAGA_REPOSITORY = Symbol('SAGA_REPOSITORY');
export interface SagaRepository {
  save(saga: TransactionSaga): Promise<void>;
  findById(sagaId: string): Promise<TransactionSaga | undefined>;
  list(): Promise<TransactionSaga[]>;
}
