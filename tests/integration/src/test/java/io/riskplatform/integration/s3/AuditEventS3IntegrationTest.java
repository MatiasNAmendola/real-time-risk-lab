package io.riskplatform.integration.s3;

import io.riskplatform.integration.IntegrationTestSupport;
import io.riskplatform.integration.containers.FlociContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that audit events can be written to and read back from a Floci S3 bucket
 * (ADR-0042) using the AWS S3 SDK with a custom endpoint.
 */
@Testcontainers
class AuditEventS3IntegrationTest extends IntegrationTestSupport {

    private static final String BUCKET = "risk-audit";

    @Container
    static final FlociContainer FLOCI = floci;

    private static S3Client s3;

    @BeforeAll
    static void setupS3Client() {
        s3 = S3Client.builder()
                .endpointOverride(URI.create(FLOCI.s3Endpoint()))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(FlociContainer.ACCESS_KEY, FlociContainer.SECRET_KEY)))
                .forcePathStyle(true)
                .httpClientBuilder(software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient.builder())
                .build();

        s3.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
    }

    @Test
    void audit_event_can_be_stored_and_retrieved_from_s3() {
        String objectId = UUID.randomUUID().toString();
        String key = "2026/05/07/" + objectId + ".json";
        String auditPayload = "{\"eventType\":\"RISK_DECISION\",\"decision\":\"APPROVE\",\"requestId\":\"" + objectId + "\",\"ts\":\"2026-05-07T00:00:00Z\"}";

        // Write
        s3.putObject(
                PutObjectRequest.builder().bucket(BUCKET).key(key).contentType("application/json").build(),
                RequestBody.fromString(auditPayload, StandardCharsets.UTF_8));

        // Read
        byte[] bytes = s3.getObject(
                GetObjectRequest.builder().bucket(BUCKET).key(key).build(),
                ResponseTransformer.toBytes()).asByteArray();

        String retrieved = new String(bytes, StandardCharsets.UTF_8);

        assertThat(retrieved).isEqualTo(auditPayload);
        assertThat(retrieved).contains("APPROVE");
        assertThat(retrieved).contains(objectId);
    }
}
