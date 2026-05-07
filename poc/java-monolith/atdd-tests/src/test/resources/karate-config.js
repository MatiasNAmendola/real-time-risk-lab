function fn() {
  var env = karate.env || 'local';

  var config = {
    baseUrl: 'http://localhost:8090',
    kafkaBroker: 'localhost:19092',
    openObserveUrl: 'http://localhost:5080',
    kafkaTopic: 'risk-decisions',
    webhookListenerHost: 'host.docker.internal'
  };

  if (env === 'ci') {
    config.baseUrl = 'http://java-monolith:8090';
    config.kafkaBroker = 'kafka:9092';
    config.openObserveUrl = 'http://openobserve:5080';
    config.webhookListenerHost = 'localhost';
  }

  karate.configure('connectTimeout', 2000);
  karate.configure('readTimeout', 10000);

  return config;
}
