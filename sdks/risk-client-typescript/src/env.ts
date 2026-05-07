import { Environment } from './types';

interface EnvCoords {
  restBaseUrl: string;
  kafkaBroker: string;
  sqsQueueUrl: string;
}

const ENV_MAP: Record<Environment, EnvCoords> = {
  PROD: {
    restBaseUrl: 'https://risk.naranjax.com',
    kafkaBroker: 'kafka.naranjax.com:9092',
    sqsQueueUrl: 'https://sqs.us-east-1.amazonaws.com/123456789/risk-decisions-prod',
  },
  STAGING: {
    restBaseUrl: 'https://risk-staging.naranjax.com',
    kafkaBroker: 'kafka-staging.naranjax.com:9092',
    sqsQueueUrl: 'https://sqs.us-east-1.amazonaws.com/123456789/risk-decisions-staging',
  },
  DEV: {
    restBaseUrl: 'https://risk-dev.naranjax.com',
    kafkaBroker: 'kafka-dev.naranjax.com:9092',
    sqsQueueUrl: 'https://sqs.us-east-1.amazonaws.com/123456789/risk-decisions-dev',
  },
  LOCAL: {
    restBaseUrl: 'http://localhost:8080',
    kafkaBroker: 'localhost:9092',
    sqsQueueUrl: 'http://localhost:4566/000000000000/risk-decisions',
  },
};

export function resolveEnv(env: Environment): EnvCoords {
  return ENV_MAP[env];
}
