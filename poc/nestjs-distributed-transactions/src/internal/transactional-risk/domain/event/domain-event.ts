export interface DomainEvent<TPayload extends object = Record<string, unknown>> {
  eventId: string;
  aggregateId: string;
  type: string;
  version: number;
  occurredAt: string;
  correlationId: string;
  payload: TPayload;
}
