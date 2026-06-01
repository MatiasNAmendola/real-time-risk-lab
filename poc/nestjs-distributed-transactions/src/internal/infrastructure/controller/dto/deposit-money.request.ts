import { ApiProperty } from '@nestjs/swagger';
import { IsIn, IsNumber, IsOptional, IsPositive } from 'class-validator';

export class DepositMoneyRequest {
  @ApiProperty({ example: 100.5 })
  @IsNumber()
  @IsPositive()
  amount!: number;

  @ApiProperty({ example: 'ARS', required: false })
  @IsOptional()
  @IsIn(['ARS', 'USD'])
  currency?: 'ARS' | 'USD';
}
