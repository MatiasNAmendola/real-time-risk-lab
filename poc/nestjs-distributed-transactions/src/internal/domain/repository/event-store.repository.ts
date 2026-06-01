import { DomainEvent } from '../event/domain-event';

export const EVENT_STORE_REPOSITORY = Symbol('EVENT_STORE_REPOSITORY');
export interface EventStoreRepository {
  append(event: DomainEvent): Promise<void>;
  stream(aggregateId: string): Promise<DomainEvent[]>;
  all(): Promise<DomainEvent[]>;
}
