function fn() {
  var env = karate.env || 'local';

  // Self-launching suite publishes the chosen port via -Dmonolith.baseUrl.
  // Falls back to the legacy :8090 when running against a pre-existing compose stack.
  var resolvedBaseUrl = karate.properties['monolith.baseUrl'] || 'http://localhost:8090';

  var config = {
    baseUrl: resolvedBaseUrl,
    kafkaBroker: 'localhost:9092',
    openObserveUrl: 'http://localhost:5080',
    kafkaTopic: 'risk-decisions',
    webhookListenerHost: 'host.docker.internal'
  };

  if (env === 'ci') {
    config.baseUrl = 'http://vertx-monolith-inprocess:8090';
    config.kafkaBroker = 'tansu:9092';
    config.openObserveUrl = 'http://openobserve:5080';
    config.webhookListenerHost = 'localhost';
  }

  karate.configure('connectTimeout', 2000);
  karate.configure('readTimeout', 10000);

  return config;
}
