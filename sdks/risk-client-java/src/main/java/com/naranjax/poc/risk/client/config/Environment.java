package com.naranjax.poc.risk.client.config;

/**
 * Deployment environment. Each value encapsulates the URL coordinates so
 * callers never construct raw endpoint strings.
 */
public enum Environment {

    PROD(
            "https://risk.naranjax.com",
            "kafka.naranjax.com:9092",
            "https://sqs.us-east-1.amazonaws.com/123456789/risk-decisions-prod"
    ),
    STAGING(
            "https://risk-staging.naranjax.com",
            "kafka-staging.naranjax.com:9092",
            "https://sqs.us-east-1.amazonaws.com/123456789/risk-decisions-staging"
    ),
    DEV(
            "https://risk-dev.naranjax.com",
            "kafka-dev.naranjax.com:9092",
            "https://sqs.us-east-1.amazonaws.com/123456789/risk-decisions-dev"
    ),
    LOCAL(
            "http://localhost:8080",
            "localhost:9092",
            "http://localhost:4566/000000000000/risk-decisions"
    );

    private final String restBaseUrl;
    private final String kafkaBroker;
    private final String sqsQueueUrl;

    Environment(String restBaseUrl, String kafkaBroker, String sqsQueueUrl) {
        this.restBaseUrl = restBaseUrl;
        this.kafkaBroker = kafkaBroker;
        this.sqsQueueUrl = sqsQueueUrl;
    }

    public String restBaseUrl()  { return restBaseUrl; }
    public String kafkaBroker()  { return kafkaBroker; }
    public String sqsQueueUrl()  { return sqsQueueUrl; }
}
