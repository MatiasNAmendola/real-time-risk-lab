package com.naranjax.interview.risk.domain.port;

/**
 * Port interface for structured logging — domain-safe abstraction.
 * Concrete implementations (ConsoleStructuredLogger, etc.) live in infrastructure.
 */
public interface StructuredLoggerPort {
    StructuredLoggerPort with(String key, Object value);
    void info(String message, Object... keyValues);
    void warn(String message, Object... keyValues);
    void error(Throwable error, String message, Object... keyValues);
}
