import { ClientOptions, HealthStatus, RiskDecision, RiskRequest } from './types';
import { fetchWithRetry } from './http';
import { resolveEnv } from './env';

export class SyncChannel {
  private readonly baseUrl: string;

  constructor(private readonly options: ClientOptions) {
    this.baseUrl = resolveEnv(options.environment).restBaseUrl;
  }

  async evaluate(req: RiskRequest): Promise<RiskDecision> {
    return fetchWithRetry<RiskDecision>(
      `${this.baseUrl}/risk`,
      { method: 'POST', body: JSON.stringify(req) },
      this.options,
    );
  }

  async evaluateBatch(reqs: RiskRequest[]): Promise<RiskDecision[]> {
    return fetchWithRetry<RiskDecision[]>(
      `${this.baseUrl}/risk/batch`,
      { method: 'POST', body: JSON.stringify(reqs) },
      this.options,
    );
  }

  async health(): Promise<HealthStatus> {
    return fetchWithRetry<HealthStatus>(
      `${this.baseUrl}/healthz`,
      { method: 'GET' },
      this.options,
    );
  }
}
