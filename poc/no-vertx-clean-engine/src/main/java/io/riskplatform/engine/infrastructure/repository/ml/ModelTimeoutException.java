package io.riskplatform.engine.infrastructure.repository.ml;

public final class ModelTimeoutException extends Exception {
    public ModelTimeoutException(String message) { super(message); }
}
