import { IsIn, IsObject, IsOptional, IsString, MinLength } from 'class-validator';
import { PublishEdaMessageInput } from '@application/dto/publish-eda-message.input';

export class PublishEdaMessageRequest implements PublishEdaMessageInput {
  @IsString()
  @MinLength(3)
  domainId!: string;

  @IsIn(['transaction', 'account', 'ledger-transfer'])
  domainType!: 'transaction' | 'account' | 'ledger-transfer';

  @IsString()
  @MinLength(3)
  eventType!: string;

  @IsOptional()
  @IsObject()
  payload?: Record<string, unknown>;
}
