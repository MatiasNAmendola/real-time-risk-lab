package io.riskplatform.distributed.usecase.aws;

import io.riskplatform.distributed.shared.RiskDecision;
import io.riskplatform.distributed.shared.RiskRequest;
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
 * Publishes risk decision audit records to MinIO (S3-compatible) via AWS SDK v2.
 *
 * Key pattern: risk-audit/{year}/{month}/{day}/{eventId}.json
 *
 * The S3Client is synchronous; calls are dispatched on Vert.x virtual-thread executor
 * via executeBlocking so the event loop is never blocked.
 *
 * Env vars (set in docker-compose.yml for usecase-app):
 *   AWS_ENDPOINT_URL_S3=http://minio:9000
 *   AWS_ACCESS_KEY_ID=test
 *   AWS_SECRET_ACCESS_KEY=test
 *   AWS_REGION=us-east-1
 *   RISK_AUDIT_BUCKET=risk-audit
 */
public final class S3AuditPublisher {

    private static final Logger log = LoggerFactory.getLogger(S3AuditPublisher.class);

    private final Vertx vertx;
    private final S3Client s3;
    private final String bucket;
    private final boolean enabled;

    public S3AuditPublisher(Vertx vertx) {
        this.vertx = vertx;
        String endpoint = System.getenv("AWS_ENDPOINT_URL_S3");
        this.bucket     = System.getenv().getOrDefault("RISK_AUDIT_BUCKET", "risk-audit");
        this.enabled    = endpoint != null && !endpoint.isBlank();

        if (this.enabled) {
            String accessKey    = System.getenv().getOrDefault(
                "S3_ACCESS_KEY_ID", System.getenv().getOrDefault("AWS_ACCESS_KEY_ID", "test"));
            String secretKey    = System.getenv().getOrDefault(
                "S3_SECRET_ACCESS_KEY", System.getenv().getOrDefault("AWS_SECRET_ACCESS_KEY", "test"));
            String region       = System.getenv().getOrDefault("AWS_REGION", "us-east-1");

            this.s3 = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.of(region))
                .forcePathStyle(true)   // required for MinIO path-style access
                .build();
            log.info("[usecase-app] S3AuditPublisher configured: endpoint={} bucket={}", endpoint, bucket);
        } else {
            this.s3 = null;
            log.info("[usecase-app] S3AuditPublisher disabled (AWS_ENDPOINT_URL_S3 not set)");
        }
    }

    /**
     * Publishes the audit event asynchronously using Vert.x executeBlocking.
     * Returns a completed Future immediately if S3 is disabled.
     */
    public Future<Void> publishAudit(RiskRequest req, RiskDecision decision, String correlationId) {
        if (!enabled) {
            return Future.succeededFuture();
        }
        return vertx.executeBlocking(() -> {
            String eventId = UUID.randomUUID().toString();
            ZonedDateTime now = Instant.now().atZone(ZoneOffset.UTC);
            String key = String.format("risk-audit/%d/%02d/%02d/%s.json",
                now.getYear(), now.getMonthValue(), now.getDayOfMonth(), eventId);

            String body = buildJson(eventId, req, decision, correlationId, now.toInstant().toString());

            s3.putObject(
                PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType("application/json")
                    .build(),
                RequestBody.fromString(body));

            log.info("[usecase-app] S3 audit published: key={} decision={}", key, decision.decision());
            return (Void) null;
        });
    }

    private String buildJson(String eventId, RiskRequest req, RiskDecision decision,
                             String correlationId, String occurredAt) {
        return "{"
            + "\"eventId\":\"" + eventId + "\","
            + "\"eventType\":\"risk.decision.evaluated\","
            + "\"eventVersion\":1,"
            + "\"correlationId\":\"" + correlationId + "\","
            + "\"transactionId\":\"" + req.transactionId() + "\","
            + "\"customerId\":\"" + req.customerId() + "\","
            + "\"amountCents\":" + req.amountCents() + ","
            + "\"decision\":\"" + decision.decision() + "\","
            + "\"reason\":\"" + decision.reason() + "\","
            + "\"occurredAt\":\"" + occurredAt + "\""
            + "}";
    }
}
