package io.riskplatform.integration.secrets;

import io.riskplatform.integration.IntegrationTestSupport;
import io.riskplatform.integration.containers.OpenBaoContainer;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates OpenBao (Vault-compatible) KV secrets engine and transit engine
 * using plain HTTP requests (no third-party vault SDK required).
 *
 * Dev mode: KV v2 is mounted at "secret/", transit must be enabled separately.
 */
@Testcontainers
class OpenBaoSecretsIntegrationTest extends IntegrationTestSupport {

    @Container
    static final OpenBaoContainer OPENBAO = openBao;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(OPENBAO.baseUrl() + path))
                .header("X-Vault-Token", OpenBaoContainer.ROOT_TOKEN)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(OPENBAO.baseUrl() + path))
                .header("X-Vault-Token", OpenBaoContainer.ROOT_TOKEN)
                .GET()
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void kv_write_and_read_secret() throws Exception {
        // KV v2 write
        HttpResponse<String> write = post(
                "/v1/secret/data/risk-engine/db",
                "{\"data\":{\"password\":\"poc\",\"username\":\"risk_user\"}}");

        assertThat(write.statusCode()).isIn(200, 204);

        // KV v2 read
        HttpResponse<String> read = get("/v1/secret/data/risk-engine/db");

        assertThat(read.statusCode()).isEqualTo(200);
        assertThat(read.body()).contains("poc");
        assertThat(read.body()).contains("risk_user");
    }

    @Test
    void transit_engine_encrypt_and_decrypt() throws Exception {
        // Enable transit engine
        HttpResponse<String> mount = post(
                "/v1/sys/mounts/transit",
                "{\"type\":\"transit\"}");
        // 200 or 400 (already mounted from a previous run in same container)
        assertThat(mount.statusCode()).isIn(200, 204, 400);

        // Create a named key
        HttpResponse<String> createKey = post(
                "/v1/transit/keys/risk-engine",
                "{\"type\":\"aes256-gcm96\"}");
        assertThat(createKey.statusCode()).isIn(200, 204);

        // Encrypt
        // plaintext must be base64-encoded
        String plaintext = java.util.Base64.getEncoder().encodeToString("secret-value".getBytes(StandardCharsets.UTF_8));
        HttpResponse<String> encrypt = post(
                "/v1/transit/encrypt/risk-engine",
                "{\"plaintext\":\"" + plaintext + "\"}");
        assertThat(encrypt.statusCode()).isEqualTo(200);
        assertThat(encrypt.body()).contains("vault:v1:");

        // Extract ciphertext
        String ciphertext = extract(encrypt.body(), "ciphertext");

        // Decrypt
        HttpResponse<String> decrypt = post(
                "/v1/transit/decrypt/risk-engine",
                "{\"ciphertext\":\"" + ciphertext + "\"}");
        assertThat(decrypt.statusCode()).isEqualTo(200);

        String decryptedB64 = extract(decrypt.body(), "plaintext");
        String decrypted = new String(java.util.Base64.getDecoder().decode(decryptedB64), StandardCharsets.UTF_8);

        assertThat(decrypted).isEqualTo("secret-value");
    }

    /**
     * Minimal JSON field extractor — avoids pulling in a JSON library just for tests.
     * Extracts the string value of a top-level field from a flat Vault response.
     */
    private static String extract(String json, String field) {
        String token = "\"" + field + "\":\"";
        int start = json.indexOf(token) + token.length();
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }
}
