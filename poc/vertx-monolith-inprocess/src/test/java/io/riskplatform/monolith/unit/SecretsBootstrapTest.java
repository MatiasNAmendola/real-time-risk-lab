package io.riskplatform.monolith.unit;

import io.riskplatform.monolith.repository.SecretsBootstrap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SecretsBootstrap.
 * Verifies fallback behavior when neither Moto nor OpenBao is configured.
 */
class SecretsBootstrapTest {

    @Test
    void resolveDbPassword_fallsBackToEnvVar_whenNoExternalServiceConfigured() {
        // VALKEY_URL and AWS endpoints not set in test environment — must return env or default
        String password = SecretsBootstrap.resolveDbPassword();
        assertThat(password).isNotNull().isNotBlank();
    }

    @Test
    void resolveDbPassword_returnsNonNull() {
        String password = SecretsBootstrap.resolveDbPassword();
        assertThat(password).isNotNull();
    }
}
