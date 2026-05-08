package io.riskplatform.distributed.repository.secrets;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

import java.net.URI;

/**
 * Reads database credentials from Moto Secrets Manager (or falls back to env vars).
 *
 * OpenBao is supported via the same Secrets Manager compatible endpoint. In dev mode,
 * OpenBao does not expose a Secrets Manager-compatible API directly, so the pattern here
 * uses Moto as the Secrets Manager mock (AWS_ENDPOINT_URL_SECRETSMANAGER=http://moto:5000).
 * For OpenBao-only environments, use OPENBAO_URL + OPENBAO_TOKEN env vars and the
 * HTTP KV API directly (see readFromOpenBao method below).
 *
 * Resolution priority:
 *   1. AWS_ENDPOINT_URL_SECRETSMANAGER set → use Moto Secrets Manager
 *   2. OPENBAO_URL set → use OpenBao KV v2 HTTP API
 *   3. Fallback → read from PG_PASSWORD env var (existing behaviour)
 *
 * Secrets seeded in Moto (via init container or moto-init.sh):
 *   "riskplatform/db-password" → the postgres password value
 */
public final class SecretsBootstrap {

    private SecretsBootstrap() {}

    /**
     * Resolves the Postgres password using the configured secrets backend.
     * Never throws — falls back to env var on any error.
     */
    public static String resolveDbPassword() {
        String motoEndpoint   = System.getenv("AWS_ENDPOINT_URL_SECRETSMANAGER");
        String openbaoUrl     = System.getenv("OPENBAO_URL");
        String secretName     = System.getenv().getOrDefault("DB_SECRET_NAME", "riskplatform/db-password");
        String envFallback    = System.getenv().getOrDefault("PG_PASSWORD", "riskplatform");

        if (motoEndpoint != null && !motoEndpoint.isBlank()) {
            try {
                return readFromSecretsManager(motoEndpoint, secretName);
            } catch (Exception e) {
                System.err.println("[repository-app] SecretsBootstrap: Moto Secrets Manager failed (" +
                    e.getMessage() + "), falling back to env var");
            }
        } else if (openbaoUrl != null && !openbaoUrl.isBlank()) {
            try {
                return readFromOpenBao(openbaoUrl, secretName);
            } catch (Exception e) {
                System.err.println("[repository-app] SecretsBootstrap: OpenBao failed (" +
                    e.getMessage() + "), falling back to env var");
            }
        }

        return envFallback;
    }

    private static String readFromSecretsManager(String endpoint, String secretName) {
        String accessKey = System.getenv().getOrDefault("AWS_ACCESS_KEY_ID", "test");
        String secretKey = System.getenv().getOrDefault("AWS_SECRET_ACCESS_KEY", "test");
        String region    = System.getenv().getOrDefault("AWS_REGION", "us-east-1");

        try (SecretsManagerClient client = SecretsManagerClient.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.of(region))
                .build()) {

            String value = client.getSecretValue(
                GetSecretValueRequest.builder().secretId(secretName).build()
            ).secretString();

            System.out.println("[repository-app] SecretsBootstrap: loaded secret '" + secretName +
                "' from Moto Secrets Manager at " + endpoint);
            return value;
        }
    }

    /**
     * Reads a secret from OpenBao KV v2 API using plain HTTP (no SDK needed for Bao).
     *
     * OpenBao KV v2 path: {OPENBAO_URL}/v1/secret/data/{secretName}
     * Response JSON: { "data": { "data": { "value": "<secret>" } } }
     *
     * Seed command (in openbao init container or manually):
     *   bao kv put secret/riskplatform/db-password value=riskplatform
     */
    private static String readFromOpenBao(String openbaoUrl, String secretName) throws Exception {
        String token = System.getenv().getOrDefault("OPENBAO_TOKEN", "root");
        // Normalize: "riskplatform/db-password" → path component for KV v2
        String kvPath = openbaoUrl.replaceAll("/$", "") + "/v1/secret/data/" + secretName;

        java.net.URL url = URI.create(kvPath).toURL();
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-Vault-Token", token);
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);

        int status = conn.getResponseCode();
        if (status != 200) {
            throw new RuntimeException("OpenBao HTTP " + status + " for path: " + kvPath);
        }

        String responseBody;
        try (java.io.InputStream is = conn.getInputStream();
             java.io.BufferedReader reader = new java.io.BufferedReader(
                 new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            responseBody = sb.toString();
        }

        // Parse {"data":{"data":{"value":"<secret>"}}}  — minimal string parsing, no JSON lib needed
        int valueIdx = responseBody.indexOf("\"value\":\"");
        if (valueIdx < 0) throw new RuntimeException("OpenBao response missing 'value' key");
        int start = valueIdx + 9;
        int end   = responseBody.indexOf('"', start);
        if (end < 0) throw new RuntimeException("OpenBao response 'value' not terminated");

        String value = responseBody.substring(start, end);
        System.out.println("[repository-app] SecretsBootstrap: loaded secret '" + secretName +
            "' from OpenBao at " + openbaoUrl);
        return value;
    }
}
