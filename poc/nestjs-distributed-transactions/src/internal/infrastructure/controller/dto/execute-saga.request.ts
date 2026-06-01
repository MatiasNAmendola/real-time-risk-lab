import { IsIn, IsNumber, IsOptional, IsPositive, IsString, MinLength } from 'class-validator';
import { ExecuteSagaInput } from '../../../application/dto/execute-saga.input';

export class ExecuteSagaRequest implements ExecuteSagaInput {
  @IsString()
  @MinLength(3)
  transactionId!: string;

  @IsString()
  debitAccountId!: string;

  @IsString()
  creditAccountId!: string;

  @IsNumber()
  @IsPositive()
  amount!: number;

  @IsIn(['ARS', 'USD'])
  currency!: 'ARS' | 'USD';

  @IsOptional()
  @IsIn(['SUCCESS', 'FAIL_AFTER_INVENTORY', 'FAIL_AFTER_LEDGER', 'FAIL_NOTIFICATION'])
  scenario?: 'SUCCESS' | 'FAIL_AFTER_INVENTORY' | 'FAIL_AFTER_LEDGER' | 'FAIL_NOTIFICATION';
}
