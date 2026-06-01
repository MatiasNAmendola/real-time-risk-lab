import { createHash, randomUUID } from 'crypto';
import { DomainMessage } from '@domain/event/domain-message';
import { PublishEdaMessageInput } from '@application/dto/publish-eda-message.input';

export function createDomainMessage(input: PublishEdaMessageInput, correlationId: string): DomainMessage {
  const payload = input.payload ?? {};
  const checksum = md5Stable({ domainId: input.domainId, domainType: input.domainType, eventType: input.eventType, payload });
  return {
    messageId: randomUUID(),
    domainId: input.domainId,
    domainType: input.domainType,
    eventType: input.eventType,
    checksumAlgorithm: 'md5',
    checksum,
    occurredAt: new Date().toISOString(),
    correlationId,
    payload,
  };
}

export function idempotencyKeyFor(message: DomainMessage): string {
  return `${message.domainType}:${message.domainId}:${message.checksum}`;
}

function md5Stable(value: unknown): string {
  return createHash('md5').update(stableStringify(value)).digest('hex');
}

function stableStringify(value: unknown): string {
  if (Array.isArray(value)) return `[${value.map(stableStringify).join(',')}]`;
  if (value && typeof value === 'object') {
    return `{${Object.entries(value as Record<string, unknown>)
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([key, entry]) => `${JSON.stringify(key)}:${stableStringify(entry)}`)
      .join(',')}}`;
  }
  return JSON.stringify(value);
}
