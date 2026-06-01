import { ExecuteDistributedTransactionUseCase } from '../internal/application/usecase/transaction/execute-distributed-transaction.usecase';
import { InMemoryAccountProjectionRepository } from '../internal/infrastructure/repository/in-memory-account-projection.repository';
import { InMemoryEventStoreRepository } from '../internal/infrastructure/repository/in-memory-event-store.repository';
import { InMemorySagaRepository } from '../internal/infrastructure/repository/in-memory-saga.repository';
import { ValkeyMessageIdempotencyRepository } from '../internal/infrastructure/repository/valkey-message-idempotency.repository';
import { TigerBeetleLedgerAdapter } from '../internal/infrastructure/tigerbeetle/tigerbeetle-ledger.adapter';
import { ProjectionEventHandlers } from '../internal/infrastructure/cqrs/projection-event-handlers';
import { HonoDomainEventPublisher } from '../internal/infrastructure/cqrs/hono-domain-event.publisher';
import { SimpleCommandBus } from '../internal/infrastructure/cqrs/simple-command-bus';
import { SimpleQueryBus } from '../internal/infrastructure/cqrs/simple-query-bus';
import { BullMqEdaService } from '../internal/infrastructure/eda/bullmq-eda.service';

export function buildContainer() {
  const sagas = new InMemorySagaRepository();
  const events = new InMemoryEventStoreRepository();
  const projections = new InMemoryAccountProjectionRepository();
  const idempotency = new ValkeyMessageIdempotencyRepository();
  const ledger = new TigerBeetleLedgerAdapter();
  const projectionHandlers = new ProjectionEventHandlers(events, projections);
  const publisher = new HonoDomainEventPublisher(projectionHandlers);
  const commandBus = new SimpleCommandBus(projectionHandlers);
  const queryBus = new SimpleQueryBus(events, projections);
  const eda = new BullMqEdaService(idempotency);
  const executeSaga = new ExecuteDistributedTransactionUseCase(sagas, events, ledger, publisher);

  return { sagas, events, projections, idempotency, ledger, projectionHandlers, publisher, commandBus, queryBus, eda, executeSaga };
}

export type AppContainer = ReturnType<typeof buildContainer>;
