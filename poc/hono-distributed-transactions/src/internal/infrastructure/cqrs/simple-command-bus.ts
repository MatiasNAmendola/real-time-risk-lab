import { DepositMoneyCommand } from '@application/command/deposit-money.command';
import { OpenAccountCommand } from '@application/command/open-account.command';
import { PostLedgerTransferCommand } from '@application/command/post-ledger-transfer.command';
import { AccountOpenedEvent, LedgerTransferPostedEvent, MoneyDepositedEvent } from '@application/event/account-events';
import { ProjectionEventHandlers } from '@infrastructure/cqrs/projection-event-handlers';

export class SimpleCommandBus {
  constructor(private readonly handlers: ProjectionEventHandlers) {}

  async openAccount(command: OpenAccountCommand): Promise<{ accepted: true; accountId: string }> {
    await this.handlers.accountOpened(new AccountOpenedEvent(command.accountId, command.currency, command.correlationId));
    return { accepted: true, accountId: command.accountId };
  }

  async depositMoney(command: DepositMoneyCommand): Promise<{ accepted: true; accountId: string; amountCents: string }> {
    await this.handlers.moneyDeposited(
      new MoneyDepositedEvent(command.accountId, command.amountCents, command.currency, command.correlationId),
    );
    return { accepted: true, accountId: command.accountId, amountCents: command.amountCents };
  }

  async postLedgerTransfer(command: PostLedgerTransferCommand): Promise<{ accepted: true; transferId: string }> {
    await this.handlers.ledgerTransferPosted(
      new LedgerTransferPostedEvent(
        command.transferId,
        command.debitAccountId,
        command.creditAccountId,
        command.amountCents,
        command.currency,
        command.correlationId,
      ),
    );
    return { accepted: true, transferId: command.transferId };
  }
}
