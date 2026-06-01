import { Injectable, OnModuleDestroy } from '@nestjs/common';
import Redis from 'ioredis';
import type { IdempotencyRecord, IdempotencyResult, MessageIdempotencyRepository } from '@domain/repository/message-idempotency.repository';

@Injectable()
export class ValkeyMessageIdempotencyRepository implements MessageIdempotencyRepository, OnModuleDestroy {
  private redis?: Redis;

  async markFirstSeen(record: IdempotencyRecord): Promise<IdempotencyResult> {
    const key = this.key(record.idempotencyKey);
    const inserted = await this.client().set(key, JSON.stringify(record), 'EX', Number(process.env.IDEMPOTENCY_TTL_SECONDS ?? 86400), 'NX');
    if (inserted === 'OK') return { accepted: true, reason: 'FIRST_SEEN', record };

    const existing = await this.find(record.idempotencyKey);
    if (!existing) return { accepted: false, reason: 'DUPLICATE_CHECKSUM_MISMATCH', record };
    return {
      accepted: false,
      reason: existing.checksum === record.checksum ? 'DUPLICATE_SAME_CHECKSUM' : 'DUPLICATE_CHECKSUM_MISMATCH',
      record: existing,
    };
  }

  async find(idempotencyKey: string): Promise<IdempotencyRecord | undefined> {
    const raw = await this.client().get(this.key(idempotencyKey));
    return raw ? (JSON.parse(raw) as IdempotencyRecord) : undefined;
  }

  async onModuleDestroy(): Promise<void> {
    this.redis?.disconnect();
  }

  private client(): Redis {
    this.redis ??= new Redis({
      host: process.env.VALKEY_HOST ?? 'localhost',
      port: Number(process.env.VALKEY_PORT ?? 6379),
      maxRetriesPerRequest: null,
    });
    return this.redis;
  }

  private key(idempotencyKey: string): string {
    return `eda:idempotency:${idempotencyKey}`;
  }
}
