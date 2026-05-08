import { SQSClient } from '@aws-sdk/client-sqs';
import { AdminChannel } from './admin';
import { ChannelFactory } from './channel';
import { EventsChannel } from './events';
import { QueueChannel } from './queue';
import { StreamChannel } from './stream';
import { SyncChannel } from './sync';
import { WebhooksChannel } from './webhooks';
import { ClientOptions } from './types';

export { ClientOptions, Environment, RiskRequest, RiskDecision, DecisionEvent,
         HealthStatus, RuleInfo, Subscription, RetryConfig } from './types';
export { RiskClientError } from './http';

/**
 * Entry point for the Risk Decision Platform Client SDK (TypeScript).
 *
 * ```typescript
 * const client = new RiskClient({
 *   environment: 'LOCAL',
 *   apiKey: process.env.RISK_API_KEY!,
 *   timeoutMs: 280,
 *   retry: { strategy: 'exponential', maxAttempts: 3 },
 * });
 *
 * const decision = await client.sync.evaluate(req);
 * ```
 */
export class RiskClient {
  readonly sync:     SyncChannel;
  readonly stream:   StreamChannel;
  readonly channel:  ChannelFactory;
  readonly events:   EventsChannel;
  readonly queue:    QueueChannel;
  readonly webhooks: WebhooksChannel;
  readonly admin:    AdminChannel;

  constructor(options: ClientOptions, sqsOverride?: SQSClient) {
    this.sync     = new SyncChannel(options);
    this.stream   = new StreamChannel(options);
    this.channel  = new ChannelFactory(options);
    this.events   = new EventsChannel(options);
    this.queue    = new QueueChannel(options, sqsOverride);
    this.webhooks = new WebhooksChannel(options);
    this.admin    = new AdminChannel(options);
  }
}
