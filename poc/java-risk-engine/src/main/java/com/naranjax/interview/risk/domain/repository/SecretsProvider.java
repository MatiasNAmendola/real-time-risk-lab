package com.naranjax.interview.risk.domain.repository;

/**
 * Port out: retrieves runtime secrets by name from a secrets manager
 * (Moto Secrets Manager mock / AWS Secrets Manager in production).
 * Impl: MotoSecretsManagerProvider (Phase 2 — requires AWS SDK v2 in classpath).
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
