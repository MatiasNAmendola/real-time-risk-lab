import { Environment } from './types';

interface EnvCoords {
  restBaseUrl: string;
  kafkaBroker: string;
  sqsQueueUrl: string;
}

const ENV_MAP: Record<Environment, EnvCoords> = {
  PROD: {
    restBaseUrl: 'https://risk.riskplatform.com',
    kafkaBroker: 'kafka.riskplatform.com:9092',
    sqsQueueUrl: 'https://sqs.us-east-1.amazonaws.com/123456789/risk-decisions-prod',
  },
  STAGING: {
    restBaseUrl: 'https://risk-staging.riskplatform.com',
    kafkaBroker: 'kafka-staging.riskplatform.com:9092',
    sqsQueueUrl: 'https://sqs.us-east-1.amazonaws.com/123456789/risk-decisions-staging',
  },
  DEV: {
    restBaseUrl: 'https://risk-dev.riskplatform.com',
    kafkaBroker: 'kafka-dev.riskplatform.com:9092',
    sqsQueueUrl: 'https://sqs.us-east-1.amazonaws.com/123456789/risk-decisions-dev',
  },
  LOCAL: {
    restBaseUrl: 'http://localhost:8080',
    kafkaBroker: 'localhost:9092',
    sqsQueueUrl: 'http://localhost:4566/000000000000/risk-decisions',
  },
};

export function resolveEnv(env: Environment): EnvCoords {
  const coords = ENV_MAP[env];
  const baseOverride = typeof process !== 'undefined' ? process.env.RISK_BASE_URL : undefined;
  if (env === 'LOCAL' && baseOverride) {
    return { ...coords, restBaseUrl: baseOverride };
  }
  return coords;
}
