package io.riskplatform.engine.infrastructure.repository.secrets;

import io.riskplatform.engine.domain.repository.SecretsProvider;

/**
 * SecretsProvider backed by AWS Secrets Manager (Moto mock in local/CI environments).
 *
 * STATUS: Skeleton only — AWS SDK v2 is NOT yet in the bare-javac classpath.
 * This class does NOT compile until Phase 2 adds Gradle + AWS SDK v2 dependencies.
 *
 * Implementation notes for Phase 2:
 *  - Use software.amazon.awssdk:secretsmanager:2.29.23 + url-connection-client:2.29.23
 *  - Build SecretsManagerClient with endpointOverride, StaticCredentialsProvider,
 *    Region.US_EAST_1.
 *  - Call GetSecretValueRequest for the given secretName.
 *  - Secrets to pre-seed in Moto:
 *      "risk-engine/db-password"  → the postgres password
 *      "risk-engine/api-key"      → a service API key
 *  - Env vars required:
 *      AWS_ENDPOINT_URL_SECRETSMANAGER=http://moto:5000  (or http://localhost:5000)
 *      AWS_ACCESS_KEY_ID=test
 *      AWS_SECRET_ACCESS_KEY=test
 *      AWS_REGION=us-east-1
 *
 * TODO(phase-2): uncomment and wire via RiskApplicationFactory once Gradle is set up.
 *
 * <pre>
 * import software.amazon.awssdk.auth.credentials.*;
 * import software.amazon.awssdk.regions.Region;
 * import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
 * import software.amazon.awssdk.services.secretsmanager.model.*;
 * import java.net.URI;
 *
 * public final class MotoSecretsManagerProvider implements SecretsProvider {
 *     private final SecretsManagerClient client;
 *
 *     public MotoSecretsManagerProvider(String endpoint) {
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
public final class MotoSecretsManagerProvider implements SecretsProvider {

    private final String endpoint;

    public MotoSecretsManagerProvider(String endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    public String getSecret(String secretName) {
        throw new UnsupportedOperationException(
            "MotoSecretsManagerProvider requires AWS SDK v2 — add Gradle in Phase 2. " +
            "endpoint=" + endpoint + " secret=" + secretName);
    }
}
