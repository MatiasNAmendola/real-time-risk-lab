import { ClientOptions, RiskDecision, RiskRequest, RuleInfo } from './types';
import { fetchWithRetry } from './http';
import { resolveEnv } from './env';

export class AdminChannel {
  private readonly baseUrl: string;

  constructor(private readonly options: ClientOptions) {
    this.baseUrl = resolveEnv(options.environment).restBaseUrl;
  }

  async listRules(): Promise<RuleInfo[]> {
    return fetchWithRetry<RuleInfo[]>(
      `${this.baseUrl}/admin/rules`,
      { method: 'GET' },
      this.options,
    );
  }

  async reloadRules(): Promise<void> {
    await fetchWithRetry<void>(
      `${this.baseUrl}/admin/rules/reload`,
      { method: 'POST', body: JSON.stringify({}) },
      this.options,
    );
  }

  async testRule(req: RiskRequest): Promise<RiskDecision> {
    return fetchWithRetry<RiskDecision>(
      `${this.baseUrl}/admin/rules/test`,
      { method: 'POST', body: JSON.stringify(req) },
      this.options,
    );
  }

  async rulesAuditTrail(): Promise<Record<string, unknown>[]> {
    return fetchWithRetry<Record<string, unknown>[]>(
      `${this.baseUrl}/admin/rules/audit`,
      { method: 'GET' },
      this.options,
    );
  }
}
