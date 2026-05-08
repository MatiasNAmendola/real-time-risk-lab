// config.js — base URLs and target resolution per service.
//
// k6 reads env vars via __ENV. The orchestrator (./nx bench k6) injects
// BASE_URL directly; this file is a fallback so individual scripts also work
// when invoked as `k6 run -e TARGET=bare bench/k6/scenarios/smoke.js`.

const TARGETS = {
  bare: __ENV.BARE_URL || 'http://localhost:8081',
  monolith: __ENV.MONO_URL || 'http://localhost:8090',
  'vertx-platform': __ENV.VRP_URL || 'http://localhost:8180',
  distributed: __ENV.DIST_URL || 'http://localhost:8080',
  // k8s = k3d / OrbStack cluster behind Traefik ingress (bench/k6/profiles/k8s.json)
  k8s: __ENV.K8S_URL || 'http://risk-engine.localhost',
};

export function resolveBaseUrl() {
  if (__ENV.BASE_URL) return __ENV.BASE_URL;
  const target = __ENV.TARGET || 'distributed';
  const url = TARGETS[target];
  if (!url) {
    throw new Error(`unknown TARGET=${target}; valid: ${Object.keys(TARGETS).join(',')}`);
  }
  return url;
}

export function riskEndpoint() {
  return `${resolveBaseUrl()}/risk`;
}

export function healthEndpoint() {
  return `${resolveBaseUrl()}/health`;
}
