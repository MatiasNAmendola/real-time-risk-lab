package io.riskplatform.engine.application.port.out;

/**
 * Port interface for circuit-breaker semantics.
 * Application layer depends on this interface; infrastructure.resilience provides the impl.
 * This breaks the application -> infrastructure.resilience direct coupling (ArchUnit Rule 4).
 */
public interface CircuitBreakerPort {
    /** Returns true when a new request is permitted (circuit is closed or half-open). */
    boolean allowRequest();
    /** Signals a successful operation; resets failure counter. */
    void success();
    /** Signals a failed operation; may trip the circuit open. */
    void failure();
}
