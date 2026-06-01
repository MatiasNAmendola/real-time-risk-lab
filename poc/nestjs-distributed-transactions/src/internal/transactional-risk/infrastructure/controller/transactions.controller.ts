import { Body, Controller, Get, Inject, NotFoundException, Param, Post, Req } from '@nestjs/common';
import { CommandBus, QueryBus } from '@nestjs/cqrs';
import { ApiOperation, ApiTags } from '@nestjs/swagger';
import type { Request } from 'express';
import { randomUUID } from 'crypto';
import { DepositMoneyRequest } from '@infrastructure/controller/dto/deposit-money.request';
import { ExecuteSagaRequest } from '@infrastructure/controller/dto/execute-saga.request';
import { PublishEdaMessageRequest } from '@infrastructure/controller/dto/publish-eda-message.request';
import { ExecuteDistributedTransactionUseCase } from '@application/usecase/transaction/execute-distributed-transaction.usecase';
import { DepositMoneyCommand } from '@application/command/deposit-money.command';
import { OpenAccountCommand } from '@application/command/open-account.command';
import { PostLedgerTransferCommand } from '@application/command/post-ledger-transfer.command';
import { GetAccountBalanceQuery } from '@application/query/get-account-balance.query';
import { decimalAmountToMinorUnits } from '@application/mapper/money.mapper';
import { ACCOUNT_PROJECTION_REPOSITORY } from '@domain/repository/account-projection.repository';
import type { AccountProjectionRepository } from '@domain/repository/account-projection.repository';
import { EVENT_STORE_REPOSITORY } from '@domain/repository/event-store.repository';
import type { EventStoreRepository } from '@domain/repository/event-store.repository';
import { SAGA_REPOSITORY } from '@domain/repository/saga.repository';
import type { SagaRepository } from '@domain/repository/saga.repository';
import { BullMqEdaService } from '@infrastructure/eda/bullmq-eda.service';

type CorrelatedRequest = Request & { correlationId?: string };

@ApiTags('distributed-transactions')
@Controller()
export class TransactionsController {
  @Get('/healthz')
  healthz() {
    return { status: 'ok', app: 'nestjs-distributed-transactions' };
  }

  constructor(
    private readonly executeSaga: ExecuteDistributedTransactionUseCase,
    private readonly commandBus: CommandBus,
    private readonly queryBus: QueryBus,
    private readonly eda: BullMqEdaService,
    @Inject(SAGA_REPOSITORY) private readonly sagas: SagaRepository,
    @Inject(EVENT_STORE_REPOSITORY) private readonly events: EventStoreRepository,
    @Inject(ACCOUNT_PROJECTION_REPOSITORY) private readonly projections: AccountProjectionRepository,
  ) {}


  @Post('/accounts/:id/open')
  @ApiOperation({ summary: 'Ejemplo simple CQRS: abre una cuenta y persiste AccountOpened en el Event Store.' })
  async openSimpleAccount(@Param('id') accountId: string, @Req() req: CorrelatedRequest) {
    return this.commandBus.execute(new OpenAccountCommand(accountId, 'ARS', this.correlationId(req)));
  }

  @Post('/accounts/:id/deposit')
  @ApiOperation({ summary: 'Ejemplo simple Event Sourcing: deposita dinero y proyecta el balance.' })
  async depositMoney(@Param('id') accountId: string, @Body() body: DepositMoneyRequest, @Req() req: CorrelatedRequest) {
    return this.commandBus.execute(
      new DepositMoneyCommand(accountId, decimalAmountToMinorUnits(body.amount), body.currency ?? 'ARS', this.correlationId(req)),
    );
  }

  @Get('/accounts/:id')
  @ApiOperation({ summary: 'Ejemplo simple Query: lee balance rehidratado desde eventos y lo compara con la proyección.' })
  async getSimpleAccount(@Param('id') accountId: string) {
    return this.queryBus.execute(new GetAccountBalanceQuery(accountId));
  }

  @Get('/accounts/:id/events')
  @ApiOperation({ summary: 'Ejemplo simple Event Store: lista eventos append-only de la cuenta.' })
  async getSimpleAccountEvents(@Param('id') accountId: string) {
    return this.events.stream(accountId);
  }

  @Post('/transactions/sagas')
  @ApiOperation({ summary: 'Execute a distributed transaction saga with optional simulated failures.' })
  async runSaga(@Body() body: ExecuteSagaRequest, @Req() req: CorrelatedRequest) {
    return this.executeSaga.execute(body, this.correlationId(req));
  }

  @Get('/transactions/sagas')
  async listSagas() {
    return this.sagas.list();
  }

  @Get('/transactions/sagas/:id')
  async getSaga(@Param('id') id: string) {
    const saga = await this.sagas.findById(id);
    if (!saga) throw new NotFoundException('saga not found');
    return saga;
  }

  @Post('/transactions/cqrs/accounts/:id/open')
  async openAccount(@Param('id') accountId: string, @Req() req: CorrelatedRequest) {
    return this.commandBus.execute(new OpenAccountCommand(accountId, 'ARS', this.correlationId(req)));
  }

  @Post('/transactions/cqrs/transfers')
  async postTransfer(@Body() body: ExecuteSagaRequest, @Req() req: CorrelatedRequest) {
    return this.commandBus.execute(
      new PostLedgerTransferCommand(
        randomUUID(),
        body.debitAccountId,
        body.creditAccountId,
        decimalAmountToMinorUnits(body.amount),
        body.currency,
        this.correlationId(req),
      ),
    );
  }

  @Get('/transactions/cqrs/accounts')
  async listProjections() {
    return this.projections.all();
  }

  @Get('/transactions/cqrs/accounts/:id/projection')
  async getProjection(@Param('id') accountId: string) {
    const projection = await this.projections.findById(accountId);
    if (!projection) throw new NotFoundException('projection not found');
    return projection;
  }


  @Post('/transactions/eda/messages')
  async publishEdaMessage(@Body() body: PublishEdaMessageRequest, @Req() req: CorrelatedRequest) {
    return this.eda.publish(body, this.correlationId(req));
  }

  @Post('/transactions/eda/jobs/:jobId/process')
  async processEdaJob(@Param('jobId') jobId: string) {
    return this.eda.processById(jobId);
  }

  @Get('/transactions/events')
  async allEvents() {
    return this.events.all();
  }

  @Get('/transactions/events/:aggregateId')
  async stream(@Param('aggregateId') aggregateId: string) {
    return this.events.stream(aggregateId);
  }

  private correlationId(req: CorrelatedRequest): string {
    return req.correlationId ?? randomUUID();
  }
}
