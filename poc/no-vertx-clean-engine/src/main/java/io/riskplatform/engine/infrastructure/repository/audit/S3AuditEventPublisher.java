package io.riskplatform.engine.infrastructure.repository.audit;

import io.riskplatform.engine.domain.entity.DecisionEvent;
import io.riskplatform.engine.domain.repository.AuditEventPublisher;

/**
 * S3-backed AuditEventPublisher.
 *
 * STATUS: Skeleton only — AWS SDK v2 is NOT yet in the bare-javac classpath.
 * This class does NOT compile until Phase 2 adds Gradle + AWS SDK v2 dependencies.
 *
 * Implementation notes for Phase 2:
 *  - Use software.amazon.awssdk:s3:2.29.23 + url-connection-client:2.29.23
 *  - Build S3Client with endpointOverride(URI.create(endpoint)), forcePathStyle(true),
 *    StaticCredentialsProvider.create(AwsBasicCredentials.create("test","test")),
 *    Region.US_EAST_1.
 *  - Key pattern: "risk-audit/{year}/{month}/{day}/{eventId}.json"
 *  - Body: MiniJson.stringify of the DecisionEvent fields.
 *  - Env vars required:
 *      FLOCI_ENDPOINT=http://floci:4566   (or http://localhost:4566 from host)
 *      RISK_AUDIT_BUCKET=risk-audit
 *      AWS_ACCESS_KEY_ID=test
 *      AWS_SECRET_ACCESS_KEY=test
 *      AWS_REGION=us-east-1
 *
 * TODO(phase-2): uncomment and wire via RiskApplicationFactory once Gradle is set up.
 *
 * <pre>
 * import software.amazon.awssdk.auth.credentials.*;
 * import software.amazon.awssdk.regions.Region;
 * import software.amazon.awssdk.services.s3.S3Client;
 * import software.amazon.awssdk.services.s3.model.*;
 * import software.amazon.awssdk.core.sync.RequestBody;
 * import java.net.URI;
 * import java.util.Map;
 *
 * public final class S3AuditEventPublisher implements AuditEventPublisher {
 *     private final S3Client s3;
 *     private final String bucket;
 *
 *     public S3AuditEventPublisher(String endpoint, String bucket) {
 *         this.s3 = S3Client.builder()
 *             .endpointOverride(URI.create(endpoint))
 *             .credentialsProvider(StaticCredentialsProvider.create(
 *                 AwsBasicCredentials.create("test", "test")))
 *             .region(Region.US_EAST_1)
 *             .forcePathStyle(true)
 *             .build();
 *         this.bucket = bucket;
 *     }
 *
 *     public void publish(DecisionEvent event) {
 *         String key = String.format("risk-audit/%d/%02d/%02d/%s.json",
 *             event.occurredAt().atZone(java.time.ZoneOffset.UTC).getYear(),
 *             event.occurredAt().atZone(java.time.ZoneOffset.UTC).getMonthValue(),
 *             event.occurredAt().atZone(java.time.ZoneOffset.UTC).getDayOfMonth(),
 *             event.eventId());
 *         String body = io.riskplatform.engine.infrastructure.controller.MiniJson.stringify(Map.of(
 *             "eventId",       event.eventId(),
 *             "eventType",     event.eventType(),
 *             "eventVersion",  event.eventVersion(),
 *             "decision",      event.decision().name(),
 *             "correlationId", event.correlationId(),
 *             "transactionId", event.transactionId(),
 *             "occurredAt",    event.occurredAt().toString(),
 *             "reason",        event.reason()
 *         ));
 *         s3.putObject(
 *             PutObjectRequest.builder().bucket(bucket).key(key)
 *                 .contentType("application/json").build(),
 *             RequestBody.fromString(body));
 *     }
 * }
 * </pre>
 */
public final class S3AuditEventPublisher implements AuditEventPublisher {

    // Phase 2 placeholder — body above is commented out intentionally so this compiles
    // without AWS SDK v2 in the classpath.

    private final String endpoint;
    private final String bucket;

    public S3AuditEventPublisher(String endpoint, String bucket) {
        this.endpoint = endpoint;
        this.bucket   = bucket;
    }

    @Override
    public void publish(DecisionEvent event) {
        throw new UnsupportedOperationException(
            "S3AuditEventPublisher requires AWS SDK v2 — add Gradle in Phase 2. " +
            "endpoint=" + endpoint + " bucket=" + bucket);
    }
}
