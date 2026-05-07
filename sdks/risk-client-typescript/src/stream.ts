import EventSource from 'eventsource';
import { ClientOptions, DecisionEvent } from './types';
import { resolveEnv } from './env';

export class StreamChannel {
  private readonly sseUrl: string;

  constructor(private readonly options: ClientOptions) {
    this.sseUrl = `${resolveEnv(options.environment).restBaseUrl}/risk/stream`;
  }

  /**
   * Returns an AsyncIterable that yields DecisionEvents from the SSE stream.
   * The caller should break/return to close the connection.
   */
  async *decisions(): AsyncIterable<DecisionEvent> {
    const headers: Record<string, string> = {
      'X-API-Key': this.options.apiKey,
    };

    const es = new EventSource(this.sseUrl, { headers } as any);

    const queue: DecisionEvent[] = [];
    let resolve: (() => void) | undefined;
    let done = false;
    let error: Error | undefined;

    es.onmessage = (event: MessageEvent) => {
      try {
        const parsed = JSON.parse(event.data) as DecisionEvent;
        queue.push(parsed);
        resolve?.();
        resolve = undefined;
      } catch { /* skip unparseable */ }
    };

    es.onerror = () => {
      done = true;
      error = new Error('SSE stream error');
      resolve?.();
      es.close();
    };

    try {
      while (!done || queue.length > 0) {
        if (queue.length > 0) {
          yield queue.shift()!;
        } else {
          await new Promise<void>((r) => { resolve = r; });
        }
      }
    } finally {
      es.close();
    }

    if (error) throw error;
  }
}
