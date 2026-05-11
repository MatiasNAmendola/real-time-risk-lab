package io.riskplatform.monolith.repository;

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
import java.util.Optional;
import java.util.UUID;

/**
 * Publishes risk decision audit records to the Floci AWS emulator (S3, ADR-0042)
 * via AWS SDK v2.
 *
 * <p>Key pattern: risk-audit/{year}/{month}/{day}/{eventId}.json
 *
 * <p>Env vars: FLOCI_ENDPOINT (or AWS_ENDPOINT_URL / legacy AWS_ENDPOINT_URL_S3),
 *             AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, AWS_REGION, RISK_AUDIT_BUCKET
 */
public final class S3AuditPublisher {

    private static final Logger log = LoggerFactory.getLogger(S3AuditPublisher.class);

    private final Vertx vertx;
    private final S3Client s3;
    private final String bucket;
    private final boolean enabled;

    public S3AuditPublisher(Vertx vertx) {
        this.vertx = vertx;
        Optional<URI> endpoint = FlociEndpoint.resolve("AWS_ENDPOINT_URL_S3");
        this.bucket  = System.getenv().getOrDefault("RISK_AUDIT_BUCKET", "risk-audit");
        this.enabled = endpoint.isPresent();

        if (this.enabled) {
            String accessKey = System.getenv().getOrDefault("AWS_ACCESS_KEY_ID", "test");
            String secretKey = System.getenv().getOrDefault("AWS_SECRET_ACCESS_KEY", "test");
            String region    = System.getenv().getOrDefault("AWS_REGION", "us-east-1");
            this.s3 = S3Client.builder()
                .endpointOverride(endpoint.get())
                .credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.of(region))
                .forcePathStyle(true)
                .build();
            log.info("[monolith] S3AuditPublisher: endpoint={} bucket={}", endpoint.get(), bucket);
        } else {
            this.s3 = null;
            log.info("[monolith] S3AuditPublisher disabled (FLOCI_ENDPOINT not set)");
        }
    }

    public Future<Void> publishAudit(RiskRequest req, RiskDecision decision, String correlationId) {
        if (!enabled) return Future.succeededFuture();
        return vertx.executeBlocking(() -> {
            String eventId = UUID.randomUUID().toString();
            ZonedDateTime now = Instant.now().atZone(ZoneOffset.UTC);
            String key = String.format("risk-audit/%d/%02d/%02d/%s.json",
                now.getYear(), now.getMonthValue(), now.getDayOfMonth(), eventId);

            String body = "{"
                + "\"eventId\":\"" + eventId + "\","
                + "\"eventType\":\"risk.decision.evaluated\","
                + "\"correlationId\":\"" + correlationId + "\","
                + "\"transactionId\":\"" + req.transactionId() + "\","
                + "\"customerId\":\"" + req.customerId() + "\","
                + "\"amountCents\":" + req.amountCents() + ","
                + "\"decision\":\"" + decision.decision() + "\","
                + "\"reason\":\"" + decision.reason() + "\","
                + "\"occurredAt\":\"" + now.toInstant() + "\""
                + "}";

            s3.putObject(
                PutObjectRequest.builder().bucket(bucket).key(key).contentType("application/json").build(),
                RequestBody.fromString(body));
            log.info("[monolith] S3 audit: key={} decision={}", key, decision.decision());
            return (Void) null;
        });
    }
}
