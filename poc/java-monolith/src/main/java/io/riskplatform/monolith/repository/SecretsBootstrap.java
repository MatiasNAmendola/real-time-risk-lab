package io.riskplatform.monolith.repository;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Resolves the Postgres password from Moto Secrets Manager, OpenBao, or env var fallback.
 *
 * Resolution priority:
 *   1. AWS_ENDPOINT_URL_SECRETSMANAGER set → Moto Secrets Manager
 *   2. OPENBAO_URL set → OpenBao KV v2 HTTP API
 *   3. Fallback → PG_PASSWORD env var
 */
public final class SecretsBootstrap {

    private SecretsBootstrap() {}

    public static String resolveDbPassword() {
        String motoEndpoint = System.getenv("AWS_ENDPOINT_URL_SECRETSMANAGER");
        String openbaoUrl   = System.getenv("OPENBAO_URL");
        String secretName   = System.getenv().getOrDefault("DB_SECRET_NAME", "riskplatform/db-password");
        String envFallback  = System.getenv().getOrDefault("PG_PASSWORD", "riskplatform");

        if (motoEndpoint != null && !motoEndpoint.isBlank()) {
            try {
                return readFromSecretsManager(motoEndpoint, secretName);
            } catch (Exception e) {
                System.err.println("[monolith] SecretsBootstrap: Moto failed (" + e.getMessage() + "), fallback");
            }
        } else if (openbaoUrl != null && !openbaoUrl.isBlank()) {
            try {
                return readFromOpenBao(openbaoUrl, secretName);
            } catch (Exception e) {
                System.err.println("[monolith] SecretsBootstrap: OpenBao failed (" + e.getMessage() + "), fallback");
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
            System.out.println("[monolith] SecretsBootstrap: loaded '" + secretName + "' from Moto");
            return value;
        }
    }

    private static String readFromOpenBao(String openbaoUrl, String secretName) throws Exception {
        String token  = System.getenv().getOrDefault("OPENBAO_TOKEN", "root");
        String kvPath = openbaoUrl.replaceAll("/$", "") + "/v1/secret/data/" + secretName;

        URI uri = URI.create(kvPath);
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-Vault-Token", token);
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);

        if (conn.getResponseCode() != 200) {
            throw new RuntimeException("OpenBao HTTP " + conn.getResponseCode());
        }

        String body;
        try (InputStream is = conn.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            body = sb.toString();
        }

        int idx = body.indexOf("\"value\":\"");
        if (idx < 0) throw new RuntimeException("OpenBao response missing 'value'");
        int start = idx + 9;
        int end   = body.indexOf('"', start);
        System.out.println("[monolith] SecretsBootstrap: loaded '" + secretName + "' from OpenBao");
        return body.substring(start, end);
    }
}
