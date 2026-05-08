// config.js — base URLs and target resolution per service.
//
// k6 reads env vars via __ENV. The orchestrator (./nx bench k6) injects
// BASE_URL directly; this file is a fallback so individual scripts also work
// when invoked as `k6 run -e TARGET=no-vertx-clean-engine bench/k6/scenarios/smoke.js`.

const TARGETS = {
  'no-vertx-clean-engine': __ENV.NO_VERTX_CLEAN_ENGINE_URL || 'http://localhost:8081',
  'vertx-monolith-inprocess': __ENV.VERTX_MONOLITH_INPROCESS_URL || 'http://localhost:8090',
  'vertx-layer-as-pod-http': __ENV.VERTX_LAYER_AS_POD_HTTP_URL || 'http://localhost:8180',
  'vertx-layer-as-pod-eventbus': __ENV.VERTX_LAYER_AS_POD_EVENTBUS_URL || 'http://localhost:8080',
  // k8s = k3d / OrbStack cluster behind Traefik ingress (bench/k6/profiles/k8s.json)
  k8s: __ENV.K8S_URL || 'http://risk-engine.localhost',
};

export function resolveBaseUrl() {
  if (__ENV.BASE_URL) return __ENV.BASE_URL;
  const target = __ENV.TARGET || 'vertx-layer-as-pod-eventbus';
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
