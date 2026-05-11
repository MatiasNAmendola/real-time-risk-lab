package io.riskplatform.monolith.repository;

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
import java.util.Optional;
import java.util.UUID;

/**
 * Publishes risk decisions to the Floci AWS emulator (SQS, ADR-0042) via AWS SDK v2.
 *
 * <p>Env vars: FLOCI_ENDPOINT (or AWS_ENDPOINT_URL / legacy AWS_ENDPOINT_URL_SQS),
 *             AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, AWS_REGION, RISK_SQS_QUEUE
 */
public final class SqsDecisionPublisher {

    private static final Logger log = LoggerFactory.getLogger(SqsDecisionPublisher.class);

    private final Vertx vertx;
    private final SqsClient sqs;
    private final String queueUrl;
    private final boolean enabled;

    public SqsDecisionPublisher(Vertx vertx) {
        this.vertx = vertx;
        Optional<URI> endpoint = FlociEndpoint.resolve("AWS_ENDPOINT_URL_SQS");
        this.enabled = endpoint.isPresent();

        if (this.enabled) {
            String accessKey = System.getenv().getOrDefault("AWS_ACCESS_KEY_ID", "test");
            String secretKey = System.getenv().getOrDefault("AWS_SECRET_ACCESS_KEY", "test");
            String region    = System.getenv().getOrDefault("AWS_REGION", "us-east-1");
            String queueName = System.getenv().getOrDefault("RISK_SQS_QUEUE", "risk-decisions-queue");

            this.sqs = SqsClient.builder()
                .endpointOverride(endpoint.get())
                .credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.of(region))
                .build();

            String resolvedUrl;
            try {
                resolvedUrl = sqs.getQueueUrl(
                    GetQueueUrlRequest.builder().queueName(queueName).build()
                ).queueUrl();
            } catch (Exception e) {
                resolvedUrl = endpoint.get() + "/000000000000/" + queueName;
                log.warn("[monolith] SQS getQueueUrl failed, fallback URL: {}", resolvedUrl);
            }
            this.queueUrl = resolvedUrl;
            log.info("[monolith] SqsDecisionPublisher: queueUrl={}", queueUrl);
        } else {
            this.sqs      = null;
            this.queueUrl = null;
            log.info("[monolith] SqsDecisionPublisher disabled (FLOCI_ENDPOINT not set)");
        }
    }

    public Future<Void> publish(RiskRequest req, RiskDecision decision, String correlationId) {
        if (!enabled) return Future.succeededFuture();
        return vertx.executeBlocking(() -> {
            String msgId = UUID.randomUUID().toString();
            String body  = "{"
                + "\"messageId\":\"" + msgId + "\","
                + "\"correlationId\":\"" + correlationId + "\","
                + "\"transactionId\":\"" + req.transactionId() + "\","
                + "\"decision\":\"" + decision.decision() + "\","
                + "\"occurredAt\":\"" + Instant.now() + "\""
                + "}";
            sqs.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(body)
                .messageDeduplicationId(msgId)
                .messageGroupId("risk-decisions")
                .build());
            log.info("[monolith] SQS published: decision={} msgId={}", decision.decision(), msgId);
            return (Void) null;
        });
    }
}
