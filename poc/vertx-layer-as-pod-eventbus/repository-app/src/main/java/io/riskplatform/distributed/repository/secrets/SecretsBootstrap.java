package io.riskplatform.distributed.repository.secrets;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

import java.net.URI;
import java.util.Optional;

/**
 * Reads database credentials from the Floci AWS emulator's Secrets Manager
 * (ADR-0042) or falls back to env vars. OpenBao was removed in ADR-0042.
 *
 * <p>Resolution priority:
 * <ol>
 *   <li>{@code FLOCI_ENDPOINT} (or {@code AWS_ENDPOINT_URL} / legacy
 *       {@code AWS_ENDPOINT_URL_SECRETSMANAGER}) set → AWS Secrets Manager
 *       API via Floci.</li>
 *   <li>Fallback → {@code PG_PASSWORD} env var.</li>
 * </ol>
 *
 * <p>Secrets seeded by floci-init: {@code riskplatform/db-password}.
 */
public final class SecretsBootstrap {

    private SecretsBootstrap() {}

    /**
     * Resolves the Postgres password using the configured secrets backend.
     * Never throws — falls back to env var on any error.
     */
    public static String resolveDbPassword() {
        Optional<URI> endpoint = FlociEndpoint.resolve("AWS_ENDPOINT_URL_SECRETSMANAGER");
        String secretName  = System.getenv().getOrDefault("DB_SECRET_NAME", "riskplatform/db-password");
        String envFallback = System.getenv().getOrDefault("PG_PASSWORD", "riskplatform");

        if (endpoint.isPresent()) {
            try {
                return readFromSecretsManager(endpoint.get(), secretName);
            } catch (Exception e) {
                System.err.println("[repository-app] SecretsBootstrap: Floci Secrets Manager failed (" +
                    e.getMessage() + "), falling back to env var");
            }
        }

        return envFallback;
    }

    private static String readFromSecretsManager(URI endpoint, String secretName) {
        String accessKey = System.getenv().getOrDefault("AWS_ACCESS_KEY_ID", "test");
        String secretKey = System.getenv().getOrDefault("AWS_SECRET_ACCESS_KEY", "test");
        String region    = System.getenv().getOrDefault("AWS_REGION", "us-east-1");

        try (SecretsManagerClient client = SecretsManagerClient.builder()
                .endpointOverride(endpoint)
                .credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.of(region))
                .build()) {

            String value = client.getSecretValue(
                GetSecretValueRequest.builder().secretId(secretName).build()
            ).secretString();

            System.out.println("[repository-app] SecretsBootstrap: loaded secret '" + secretName +
                "' from Floci Secrets Manager at " + endpoint);
            return value;
        }
    }
}
