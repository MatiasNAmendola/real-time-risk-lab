package io.riskplatform.rules.client;

/**
 * Unchecked exception thrown by the SDK when a non-retryable error occurs.
 */
public class RiskClientException extends RuntimeException {

    private final int statusCode;

    public RiskClientException(String message) {
        super(message);
        this.statusCode = -1;
    }

    public RiskClientException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public RiskClientException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
    }

    /** HTTP status code if the failure originated from an HTTP response; -1 otherwise. */
    public int statusCode() { return statusCode; }
}
