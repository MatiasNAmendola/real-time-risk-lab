import WebSocket from 'ws';
import { ClientOptions, RiskDecision, RiskRequest } from './types';
import { resolveEnv } from './env';

export class RiskChannel {
  private readonly inbox: RiskDecision[] = [];
  private readonly waiters: Array<(d: RiskDecision) => void> = [];

  constructor(private readonly ws: WebSocket) {
    ws.on('message', (data) => {
      try {
        const decision = JSON.parse(data.toString()) as RiskDecision;
        const waiter = this.waiters.shift();
        if (waiter) {
          waiter(decision);
        } else {
          this.inbox.push(decision);
        }
      } catch { /* skip */ }
    });
  }

  send(req: RiskRequest): Promise<void> {
    return new Promise((resolve, reject) => {
      this.ws.send(JSON.stringify(req), (err) => {
        if (err) reject(err); else resolve();
      });
    });
  }

  receive(timeoutMs = 2000): Promise<RiskDecision | null> {
    if (this.inbox.length > 0) {
      return Promise.resolve(this.inbox.shift()!);
    }
    return new Promise((resolve) => {
      const timer = setTimeout(() => {
        const idx = this.waiters.indexOf(resolve);
        if (idx !== -1) this.waiters.splice(idx, 1);
        resolve(null);
      }, timeoutMs);

      this.waiters.push((d) => {
        clearTimeout(timer);
        resolve(d);
      });
    });
  }

  close(): void {
    this.ws.close(1000, 'client close');
  }
}

export class ChannelFactory {
  private readonly wsUrl: string;

  constructor(private readonly options: ClientOptions) {
    const base = resolveEnv(options.environment).restBaseUrl
      .replace('https://', 'wss://')
      .replace('http://', 'ws://');
    this.wsUrl = `${base}/ws/risk`;
  }

  open(): Promise<RiskChannel> {
    return new Promise((resolve, reject) => {
      const ws = new WebSocket(this.wsUrl, {
        headers: { 'X-API-Key': this.options.apiKey },
      });
      ws.once('open', () => resolve(new RiskChannel(ws)));
      ws.once('error', reject);
    });
  }
}
