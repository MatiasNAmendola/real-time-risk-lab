package com.naranjax.interview.risk.application.common;

import com.naranjax.interview.risk.domain.port.StructuredLoggerPort;

/**
 * Application-layer alias for StructuredLoggerPort.
 * Extending the domain port keeps backward compatibility with existing infrastructure
 * implementations (ConsoleStructuredLogger) that implement this interface.
 */
public interface StructuredLogger extends StructuredLoggerPort {
    StructuredLogger with(String key, Object value);
}
