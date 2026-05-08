package io.riskplatform.integration.secrets;

import io.riskplatform.integration.IntegrationTestSupport;
import io.riskplatform.integration.containers.MotoContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates AWS Secrets Manager operations against Moto (mock AWS).
 * Covers create-secret and get-secret-value.
 */
@Testcontainers
class MotoSecretsManagerIntegrationTest extends IntegrationTestSupport {

    private static final String SECRET_NAME = "risk-engine/db-password";
    private static final String SECRET_VALUE = "superSecret";

    @Container
    static final MotoContainer MOTO = moto;

    private static SecretsManagerClient secretsClient;

    @BeforeAll
    static void setupClient() {
        secretsClient = SecretsManagerClient.builder()
                .endpointOverride(URI.create(MOTO.endpointUrl()))
                .region(Region.of(MotoContainer.REGION))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(MotoContainer.ACCESS_KEY, MotoContainer.SECRET_KEY)))
                .httpClientBuilder(software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient.builder())
                .build();
    }

    @Test
    void create_secret_and_read_it_back() {
        secretsClient.createSecret(CreateSecretRequest.builder()
                .name(SECRET_NAME)
                .secretString(SECRET_VALUE)
                .build());

        GetSecretValueResponse response = secretsClient.getSecretValue(
                GetSecretValueRequest.builder().secretId(SECRET_NAME).build());

        assertThat(response.secretString()).isEqualTo(SECRET_VALUE);
    }

    @Test
    void read_existing_secret_returns_correct_value() {
        String anotherSecret = "risk-engine/api-key";
        String anotherValue = "tok_test_abc123";

        secretsClient.createSecret(CreateSecretRequest.builder()
                .name(anotherSecret)
                .secretString(anotherValue)
                .build());

        GetSecretValueResponse response = secretsClient.getSecretValue(
                GetSecretValueRequest.builder().secretId(anotherSecret).build());

        assertThat(response.secretString()).isEqualTo(anotherValue);
        assertThat(response.name()).isEqualTo(anotherSecret);
    }
}
