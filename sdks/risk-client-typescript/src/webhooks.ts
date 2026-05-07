import * as crypto from 'crypto';
import { ClientOptions, Subscription } from './types';
import { fetchWithRetry } from './http';
import { resolveEnv } from './env';

export class WebhooksChannel {
  private readonly baseUrl: string;

  constructor(private readonly options: ClientOptions) {
    this.baseUrl = resolveEnv(options.environment).restBaseUrl;
  }

  async subscribe(callbackUrl: string, eventFilter: string): Promise<Subscription> {
    return fetchWithRetry<Subscription>(
      `${this.baseUrl}/webhook/register`,
      {
        method: 'POST',
        body: JSON.stringify({
          callbackUrl,
          events: eventFilter.split(','),
        }),
      },
      this.options,
    );
  }

  async unsubscribe(subscriptionId: string): Promise<void> {
    await fetchWithRetry<void>(
      `${this.baseUrl}/webhook/unregister/${subscriptionId}`,
      { method: 'POST', body: JSON.stringify({}) },
      this.options,
    );
  }

  async list(): Promise<Subscription[]> {
    return fetchWithRetry<Subscription[]>(
      `${this.baseUrl}/webhook/subscriptions`,
      { method: 'GET' },
      this.options,
    );
  }

  /**
   * Verify an inbound webhook payload against its HMAC-SHA256 signature.
   * Uses a constant-time comparison to prevent timing attacks.
   */
  verify(payload: Buffer | string, signature: string, signingSecret: string): boolean {
    const hmac = crypto
      .createHmac('sha256', signingSecret)
      .update(payload)
      .digest('hex');

    try {
      return crypto.timingSafeEqual(
        Buffer.from(hmac,      'hex'),
        Buffer.from(signature, 'hex'),
      );
    } catch {
      return false;
    }
  }
}
