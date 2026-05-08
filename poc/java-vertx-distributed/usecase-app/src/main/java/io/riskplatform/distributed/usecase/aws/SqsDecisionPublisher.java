package io.riskplatform.distributed.usecase.aws;

import io.riskplatform.distributed.shared.RiskDecision;
import io.riskplatform.distributed.shared.RiskRequest;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

/**
 * Alternative async output: publishes risk decisions to an SQS queue (ElasticMQ in local/CI).
 *
 * Demonstrates dual-output architecture: the same decision is published to both
 * Kafka (Redpanda) for stream consumers and SQS for pull-based integrations.
 *
 * Queue name: risk-decisions-queue (created by elasticmq default or init container).
 *
 * Env vars (set in docker-compose.yml for usecase-app):
 *   AWS_ENDPOINT_URL_SQS=http://elasticmq:9324
 *   AWS_ACCESS_KEY_ID=test
 *   AWS_SECRET_ACCESS_KEY=test
 *   AWS_REGION=us-east-1
 *   RISK_SQS_QUEUE=risk-decisions-queue
 */
public final class SqsDecisionPublisher {

    private static final Logger log = LoggerFactory.getLogger(SqsDecisionPublisher.class);
    private static final String DEFAULT_QUEUE = "risk-decisions-queue";

    private final Vertx vertx;
    private final SqsClient sqs;
    private final String queueUrl;
    private final boolean enabled;

    public SqsDecisionPublisher(Vertx vertx) {
        this.vertx = vertx;
        String endpoint = System.getenv("AWS_ENDPOINT_URL_SQS");
        this.enabled    = endpoint != null && !endpoint.isBlank();

        if (this.enabled) {
            String accessKey = System.getenv().getOrDefault("AWS_ACCESS_KEY_ID", "test");
            String secretKey = System.getenv().getOrDefault("AWS_SECRET_ACCESS_KEY", "test");
            String region    = System.getenv().getOrDefault("AWS_REGION", "us-east-1");
            String queueName = System.getenv().getOrDefault("RISK_SQS_QUEUE", DEFAULT_QUEUE);

            this.sqs = SqsClient.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.of(region))
                .build();

            // Resolve queue URL at construction time (ElasticMQ path: http://elasticmq:9324/000000000000/{queue})
            String resolvedUrl;
            try {
                resolvedUrl = sqs.getQueueUrl(
                    GetQueueUrlRequest.builder().queueName(queueName).build()
                ).queueUrl();
            } catch (Exception e) {
                // Queue may not exist yet; fall back to constructed URL pattern
                resolvedUrl = endpoint + "/000000000000/" + queueName;
                log.warn("[usecase-app] SQS getQueueUrl failed, using fallback URL: {}", resolvedUrl);
            }
            this.queueUrl = resolvedUrl;
            log.info("[usecase-app] SqsDecisionPublisher configured: queueUrl={}", queueUrl);
        } else {
            this.sqs      = null;
            this.queueUrl = null;
            log.info("[usecase-app] SqsDecisionPublisher disabled (AWS_ENDPOINT_URL_SQS not set)");
        }
    }

    /**
     * Publishes the decision to SQS asynchronously via Vert.x executeBlocking.
     * Returns a completed Future immediately if SQS is disabled.
     */
    public Future<Void> publish(RiskRequest req, RiskDecision decision, String correlationId) {
        if (!enabled) {
            return Future.succeededFuture();
        }
        return vertx.executeBlocking(() -> {
            String messageId = UUID.randomUUID().toString();
            String body = "{"
                + "\"messageId\":\"" + messageId + "\","
                + "\"correlationId\":\"" + correlationId + "\","
                + "\"transactionId\":\"" + req.transactionId() + "\","
                + "\"customerId\":\"" + req.customerId() + "\","
                + "\"amountCents\":" + req.amountCents() + ","
                + "\"decision\":\"" + decision.decision() + "\","
                + "\"reason\":\"" + decision.reason() + "\","
                + "\"occurredAt\":\"" + Instant.now() + "\""
                + "}";

            SendMessageRequest.Builder request = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(body);

            // FIFO-only fields break standard ElasticMQ queues. Keep the local
            // demo on a standard queue unless the configured URL/name is FIFO.
            if (queueUrl.endsWith(".fifo")) {
                request.messageGroupId("risk-decisions")
                    .messageDeduplicationId(messageId);
            }

            sqs.sendMessage(request.build());

            log.info("[usecase-app] SQS published: queueUrl={} decision={} messageId={}",
                queueUrl, decision.decision(), messageId);
            return (Void) null;
        });
    }
}
