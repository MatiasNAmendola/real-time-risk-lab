package io.riskplatform.engine.infrastructure.repository.secrets;

import io.riskplatform.engine.domain.repository.SecretsProvider;

/**
 * SecretsProvider backed by environment variables.
 * Active fallback when SECRETS_ENDPOINT (Moto) is not configured.
 *
 * Mapping: secret name → env var name uses uppercase with dashes replaced by underscores.
 * Example: "risk-engine/db-password" → "RISK_ENGINE_DB_PASSWORD"
 *
 * TODO(phase-2): replace with MotoSecretsManagerProvider when AWS SDK v2 is available.
 */
public final class EnvSecretsProvider implements SecretsProvider {

    @Override
    public String getSecret(String secretName) {
        String envKey = secretName.toUpperCase()
            .replace("/", "_")
            .replace("-", "_");
        String value = System.getenv(envKey);
        if (value == null) {
            throw new SecretsProviderException(
                "Secret not found in environment: " + envKey + " (derived from: " + secretName + ")", null);
        }
        return value;
    }
}
