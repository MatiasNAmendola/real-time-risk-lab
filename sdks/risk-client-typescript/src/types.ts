/**
 * Core domain types mirroring the Java SDK records.
 */

export type DecisionOutcome = 'APPROVE' | 'DECLINE' | 'REVIEW';

export interface RiskRequest {
  transactionId: string;
  customerId: string;
  amountCents: number;
  correlationId?: string;
  idempotencyKey?: string;
  newDevice?: boolean;
  // Optional/extension fields tolerated by server.
  deviceId?: string;
  merchantId?: string;
  channel?: string;
}

export interface RiskDecision {
  transactionId: string;
  decision: DecisionOutcome;
  reason: string;
  elapsedMs?: number;
}

export interface DecisionEvent {
  eventId: string;
  eventType: string;
  eventVersion: number;
  occurredAt: string;
  correlationId: string;
  transactionId: string;
  decision: DecisionOutcome;
  reason: string;
}

export interface HealthStatus {
  status: 'UP' | 'DOWN';
  version?: string;
}

export interface RuleInfo {
  id: string;
  name: string;
  description: string;
  enabled: boolean;
  priority: number;
}

export interface Subscription {
  id: string;
  callbackUrl: string;
  eventFilter: string;
  createdAt: string;
}

export type Environment = 'PROD' | 'STAGING' | 'DEV' | 'LOCAL';

export interface RetryConfig {
  strategy: 'none' | 'fixed' | 'exponential';
  maxAttempts?: number;
  initialDelayMs?: number;
  multiplier?: number;
}

export interface ClientOptions {
  environment: Environment;
  apiKey: string;
  timeoutMs?: number;
  retry?: RetryConfig;
  otlpEndpoint?: string;
}
