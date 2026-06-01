package io.riskplatform.riskdecision.cleanengine.infrastructure.repository.ml;

public final class ModelTimeoutException extends Exception {
    public ModelTimeoutException(String message) { super(message); }
}
