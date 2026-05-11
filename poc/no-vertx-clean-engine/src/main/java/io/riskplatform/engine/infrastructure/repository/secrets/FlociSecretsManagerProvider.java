package io.riskplatform.engine.infrastructure.repository.secrets;

import io.riskplatform.engine.domain.repository.SecretsProvider;

/**
 * SecretsProvider backed by AWS Secrets Manager (Floci emulator, ADR-0042, in local/CI).
 *
 * STATUS: Skeleton only — AWS SDK v2 is NOT yet in the bare-javac classpath.
 * This class does NOT compile against AWS APIs until Phase 2 adds Gradle + AWS SDK v2
 * dependencies. The throwing impl below is a placeholder.
 *
 * Implementation notes for Phase 2:
 *  - Use software.amazon.awssdk:secretsmanager:2.29.23 + url-connection-client:2.29.23
 *  - Build SecretsManagerClient with endpointOverride, StaticCredentialsProvider,
 *    Region.US_EAST_1.
 *  - Call GetSecretValueRequest for the given secretName.
 *  - Secrets to pre-seed in Floci (floci-init job):
 *      "risk-engine/db-password"  → the postgres password
 *      "risk-engine/api-key"      → a service API key
 *  - Env vars required:
 *      FLOCI_ENDPOINT=http://floci:4566   (or http://localhost:4566 from host)
 *      AWS_ACCESS_KEY_ID=test
 *      AWS_SECRET_ACCESS_KEY=test
 *      AWS_REGION=us-east-1
 *
 * <pre>
 * import software.amazon.awssdk.auth.credentials.*;
 * import software.amazon.awssdk.regions.Region;
 * import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
 * import software.amazon.awssdk.services.secretsmanager.model.*;
 * import java.net.URI;
 *
 * public final class FlociSecretsManagerProvider implements SecretsProvider {
 *     private final SecretsManagerClient client;
 *
 *     public FlociSecretsManagerProvider(String endpoint) {
 *         this.client = SecretsManagerClient.builder()
 *             .endpointOverride(URI.create(endpoint))
 *             .credentialsProvider(StaticCredentialsProvider.create(
 *                 AwsBasicCredentials.create("test", "test")))
 *             .region(Region.US_EAST_1)
 *             .build();
 *     }
 *
 *     public String getSecret(String secretName) {
 *         try {
 *             GetSecretValueResponse response = client.getSecretValue(
 *                 GetSecretValueRequest.builder().secretId(secretName).build());
 *             return response.secretString();
 *         } catch (Exception e) {
 *             throw new SecretsProviderException("Failed to retrieve secret: " + secretName, e);
 *         }
 *     }
 * }
 * </pre>
 */
public final class FlociSecretsManagerProvider implements SecretsProvider {

    private final String endpoint;

    public FlociSecretsManagerProvider(String endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    public String getSecret(String secretName) {
        throw new UnsupportedOperationException(
            "FlociSecretsManagerProvider requires AWS SDK v2 — add Gradle in Phase 2. " +
            "endpoint=" + endpoint + " secret=" + secretName);
    }
}
