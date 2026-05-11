package io.riskplatform.engine.domain.repository;

/**
 * Port out: retrieves runtime secrets by name from a secrets manager
 * (Floci AWS Secrets Manager emulator in local/CI per ADR-0042; AWS Secrets Manager
 * in production).
 * Impl: FlociSecretsManagerProvider (Phase 2 — requires AWS SDK v2 in classpath).
 * Fallback: EnvSecretsProvider (current — reads from environment variables).
 */
public interface SecretsProvider {
    /**
     * Returns the secret value for the given secret name.
     * Throws {@link SecretsProviderException} if the secret cannot be retrieved.
     */
    String getSecret(String secretName);

    class SecretsProviderException extends RuntimeException {
        public SecretsProviderException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
