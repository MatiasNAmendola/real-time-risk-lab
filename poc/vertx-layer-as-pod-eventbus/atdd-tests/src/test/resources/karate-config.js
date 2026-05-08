function fn() {
  var env = karate.env || 'local';

  var config = {
    baseUrl: 'http://localhost:8080',
    kafkaBroker: 'localhost:19092',
    openObserveUrl: 'http://localhost:5080',
    kafkaTopic: 'risk-decisions',
    // When the tests run inside a Docker container, use host.docker.internal
    // to reach a WebhookListener started on the host machine.
    webhookListenerHost: 'host.docker.internal'
  };

  if (env === 'ci') {
    // Inside docker-compose CI network — use service DNS names
    config.baseUrl = 'http://controller-app:8080';
    config.kafkaBroker = 'kafka:9092';
    config.openObserveUrl = 'http://openobserve:5080';
    // In CI the listener runs inside the same network, so localhost is fine
    config.webhookListenerHost = 'localhost';
  }

  // Fail fast with a useful message if the controller is not reachable.
  // Karate will throw a ConnectException before running any scenario.
  karate.configure('connectTimeout', 2000);
  karate.configure('readTimeout', 10000);

  return config;
}
