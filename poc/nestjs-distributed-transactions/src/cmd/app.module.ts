import { MiddlewareConsumer, Module, NestModule } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { CqrsModule } from '@nestjs/cqrs';
import { TransactionsController } from '../internal/infrastructure/controller/transactions.controller';
import { ExecuteDistributedTransactionUseCase } from '../internal/application/usecase/transaction/execute-distributed-transaction.usecase';
import { CommandHandlers, QueryHandlers } from '../internal/infrastructure/cqrs/cqrs.handlers';
import { EventSourcingHandlers } from '../internal/infrastructure/cqrs/event-sourcing.handlers';
import { ACCOUNT_PROJECTION_REPOSITORY } from '../internal/domain/repository/account-projection.repository';
import { EVENT_STORE_REPOSITORY } from '../internal/domain/repository/event-store.repository';
import { SAGA_REPOSITORY } from '../internal/domain/repository/saga.repository';
import { TIGERBEETLE_LEDGER } from '../internal/domain/service/tigerbeetle-ledger.port';
import { InMemoryAccountProjectionRepository } from '../internal/infrastructure/repository/in-memory-account-projection.repository';
import { InMemoryEventStoreRepository } from '../internal/infrastructure/repository/in-memory-event-store.repository';
import { InMemorySagaRepository } from '../internal/infrastructure/repository/in-memory-saga.repository';
import { TigerBeetleLedgerAdapter } from '../internal/infrastructure/tigerbeetle/tigerbeetle-ledger.adapter';
import { CorrelationMiddleware } from '../internal/infrastructure/observability/correlation.middleware';
import { NestDomainEventPublisher } from '../internal/infrastructure/cqrs/nest-domain-event.publisher';
import { MESSAGE_IDEMPOTENCY_REPOSITORY } from '../internal/domain/repository/message-idempotency.repository';
import { ValkeyMessageIdempotencyRepository } from '../internal/infrastructure/repository/valkey-message-idempotency.repository';
import { BullMqEdaService } from '../internal/infrastructure/eda/bullmq-eda.service';

@Module({
  imports: [ConfigModule.forRoot({ isGlobal: true }), CqrsModule.forRoot()],
  controllers: [TransactionsController],
  providers: [
    BullMqEdaService,
    {
      provide: ExecuteDistributedTransactionUseCase,
      useFactory: (sagas, events, ledger, publisher) =>
        new ExecuteDistributedTransactionUseCase(sagas, events, ledger, publisher),
      inject: [SAGA_REPOSITORY, EVENT_STORE_REPOSITORY, TIGERBEETLE_LEDGER, NestDomainEventPublisher],
    },

    NestDomainEventPublisher,
    ...CommandHandlers,
    ...QueryHandlers,
    ...EventSourcingHandlers,
    { provide: SAGA_REPOSITORY, useClass: InMemorySagaRepository },
    { provide: EVENT_STORE_REPOSITORY, useClass: InMemoryEventStoreRepository },
    { provide: ACCOUNT_PROJECTION_REPOSITORY, useClass: InMemoryAccountProjectionRepository },
    { provide: TIGERBEETLE_LEDGER, useClass: TigerBeetleLedgerAdapter },
    { provide: MESSAGE_IDEMPOTENCY_REPOSITORY, useClass: ValkeyMessageIdempotencyRepository },
  ],
})
export class AppModule implements NestModule {
  configure(consumer: MiddlewareConsumer): void {
    consumer.apply(CorrelationMiddleware).forRoutes('*');
  }
}
