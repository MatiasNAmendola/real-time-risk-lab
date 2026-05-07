import { ClientOptions, RetryConfig } from './types';

export class RiskClientError extends Error {
  constructor(
    message: string,
    public readonly statusCode?: number,
  ) {
    super(message);
    this.name = 'RiskClientError';
  }
}

async function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export async function fetchWithRetry<T>(
  url: string,
  init: RequestInit,
  options: ClientOptions,
): Promise<T> {
  const retry: RetryConfig = options.retry ?? { strategy: 'exponential', maxAttempts: 3, initialDelayMs: 100, multiplier: 2 };
  const maxAttempts = retry.maxAttempts ?? 3;
  let delayMs = retry.initialDelayMs ?? 100;
  const multiplier = retry.multiplier ?? 2;

  const controller = new AbortController();
  const timeoutMs = options.timeoutMs ?? 280;
  const timer = setTimeout(() => controller.abort(), timeoutMs);

  const reqInit: RequestInit = {
    ...init,
    signal: controller.signal,
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
      'X-API-Key': options.apiKey,
      ...(init.headers as Record<string, string> | undefined),
    },
  };

  let lastError: Error | undefined;
  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    try {
      const resp = await fetch(url, reqInit);
      clearTimeout(timer);

      if (resp.ok) {
        if (resp.status === 204) return undefined as unknown as T;
        return (await resp.json()) as T;
      }

      if (resp.status >= 500 && attempt < maxAttempts) {
        await sleep(delayMs);
        delayMs = Math.floor(delayMs * multiplier);
        continue;
      }

      throw new RiskClientError(`HTTP ${resp.status} from ${url}`, resp.status);
    } catch (err) {
      if (err instanceof RiskClientError) throw err;
      lastError = err as Error;
      if (attempt < maxAttempts) {
        await sleep(delayMs);
        delayMs = Math.floor(delayMs * multiplier);
      }
    }
  }

  clearTimeout(timer);
  throw new RiskClientError(`Request to ${url} failed after ${maxAttempts} attempts: ${lastError?.message}`);
}
