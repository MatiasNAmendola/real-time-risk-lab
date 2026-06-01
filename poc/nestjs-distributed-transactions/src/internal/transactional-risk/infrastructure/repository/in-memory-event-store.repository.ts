import { Injectable } from '@nestjs/common';
import { DomainEvent } from '@domain/event/domain-event';
import { EventStoreRepository } from '@domain/repository/event-store.repository';

@Injectable()
export class InMemoryEventStoreRepository implements EventStoreRepository {
  private readonly events: DomainEvent[] = [];
  async append(event: DomainEvent): Promise<void> { this.events.push(event); }
  async stream(aggregateId: string): Promise<DomainEvent[]> {
    return this.events.filter((event) => event.aggregateId === aggregateId || Object.values(event.payload).includes(aggregateId));
  }
  async all(): Promise<DomainEvent[]> { return [...this.events]; }
}
