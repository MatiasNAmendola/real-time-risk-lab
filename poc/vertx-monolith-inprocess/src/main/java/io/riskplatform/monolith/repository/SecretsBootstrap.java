package io.riskplatform.monolith.repository;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

import java.net.URI;
import java.util.Optional;

/**
 * Resolves the Postgres password from the Floci AWS emulator's Secrets Manager
 * (ADR-0042), with an env-var fallback.
 *
 * <p>Resolution priority:
 * <ol>
 *   <li>{@code FLOCI_ENDPOINT} (or {@code AWS_ENDPOINT_URL} / legacy
 *       {@code AWS_ENDPOINT_URL_SECRETSMANAGER}) set → AWS Secrets Manager API
 *       via Floci.</li>
 *   <li>Fallback → {@code PG_PASSWORD} env var (or hard-coded default).</li>
 * </ol>
 *
 * <p>OpenBao was removed in ADR-0042; Floci's Secrets Manager replaces it.
 */
public final class SecretsBootstrap {

    private SecretsBootstrap() {}

    public static String resolveDbPassword() {
        Optional<URI> endpoint = FlociEndpoint.resolve("AWS_ENDPOINT_URL_SECRETSMANAGER");
        String secretName  = System.getenv().getOrDefault("DB_SECRET_NAME", "riskplatform/db-password");
        String envFallback = System.getenv().getOrDefault("PG_PASSWORD", "riskplatform");

        if (endpoint.isPresent()) {
            try {
                return readFromSecretsManager(endpoint.get(), secretName);
            } catch (Exception e) {
                System.err.println("[monolith] SecretsBootstrap: Floci Secrets Manager failed ("
                    + e.getMessage() + "), falling back to env var");
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
            System.out.println("[monolith] SecretsBootstrap: loaded '" + secretName + "' from Floci Secrets Manager");
            return value;
        }
    }
}
