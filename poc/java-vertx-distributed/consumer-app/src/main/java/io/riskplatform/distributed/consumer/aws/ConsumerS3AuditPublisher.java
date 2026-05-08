package io.riskplatform.distributed.consumer.aws;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Publishes a DECLINE or REVIEW audit record to MinIO (S3-compatible) from consumer-app.
 *
 * This demonstrates that the same audit concern can be applied at both the source
 * (usecase-app, synchronously with the decision) and at the consumer (async, after
 * the decision has propagated via Kafka).
 *
 * Key pattern: risk-audit/consumer/{year}/{month}/{day}/{eventId}.json
 *
 * Env vars (set in docker-compose.yml for consumer-app):
 *   AWS_ENDPOINT_URL_S3=http://minio:9000
 *   AWS_ACCESS_KEY_ID=test
 *   AWS_SECRET_ACCESS_KEY=test
 *   AWS_REGION=us-east-1
 *   RISK_AUDIT_BUCKET=risk-audit
 */
public final class ConsumerS3AuditPublisher {

    private static final Logger log = LoggerFactory.getLogger(ConsumerS3AuditPublisher.class);

    private final Vertx vertx;
    private final S3Client s3;
    private final String bucket;
    private final boolean enabled;

    public ConsumerS3AuditPublisher(Vertx vertx) {
        this.vertx = vertx;
        String endpoint = System.getenv("AWS_ENDPOINT_URL_S3");
        this.bucket     = System.getenv().getOrDefault("RISK_AUDIT_BUCKET", "risk-audit");
        this.enabled    = endpoint != null && !endpoint.isBlank();

        if (this.enabled) {
            String accessKey = System.getenv().getOrDefault(
                "S3_ACCESS_KEY_ID", System.getenv().getOrDefault("AWS_ACCESS_KEY_ID", "test"));
            String secretKey = System.getenv().getOrDefault(
                "S3_SECRET_ACCESS_KEY", System.getenv().getOrDefault("AWS_SECRET_ACCESS_KEY", "test"));
            String region    = System.getenv().getOrDefault("AWS_REGION", "us-east-1");

            this.s3 = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.of(region))
                .forcePathStyle(true)
                .build();
            log.info("[consumer-app] ConsumerS3AuditPublisher configured: endpoint={} bucket={}",
                endpoint, bucket);
        } else {
            this.s3 = null;
            log.info("[consumer-app] ConsumerS3AuditPublisher disabled (AWS_ENDPOINT_URL_S3 not set)");
        }
    }

    /**
     * Publishes a DECLINE or REVIEW record.
     * Only publishes for DECLINE and REVIEW decisions — APPROVE decisions are not audited
     * at the consumer level (they are already audited by usecase-app).
     */
    public Future<Void> publishIfHighRisk(String decision, String transactionId,
                                          String correlationId, String occurredAt,
                                          String traceparent) {
        if (!enabled) return Future.succeededFuture();
        if (!"DECLINE".equals(decision) && !"REVIEW".equals(decision)) {
            return Future.succeededFuture();
        }

        return vertx.executeBlocking(() -> {
            String eventId = UUID.randomUUID().toString();
            ZonedDateTime now = Instant.now().atZone(ZoneOffset.UTC);
            String key = String.format("risk-audit/consumer/%d/%02d/%02d/%s.json",
                now.getYear(), now.getMonthValue(), now.getDayOfMonth(), eventId);

            String body = "{"
                + "\"eventId\":\"" + eventId + "\","
                + "\"source\":\"consumer-app\","
                + "\"decision\":\"" + decision + "\","
                + "\"transactionId\":\"" + transactionId + "\","
                + "\"correlationId\":\"" + correlationId + "\","
                + "\"occurredAt\":\"" + occurredAt + "\","
                + "\"traceparent\":\"" + (traceparent != null ? traceparent : "") + "\","
                + "\"auditedAt\":\"" + Instant.now() + "\""
                + "}";

            s3.putObject(
                PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType("application/json")
                    .build(),
                RequestBody.fromString(body));

            log.info("[consumer-app] S3 high-risk audit published: key={} decision={}", key, decision);
            return (Void) null;
        });
    }
}
