package com.naranjax.interview.risk.infrastructure.repository.log;

import com.naranjax.interview.risk.application.common.StructuredLogger;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ConsoleStructuredLogger implements StructuredLogger {
    private final Map<String, Object> fields;
    private final boolean silent;

    public ConsoleStructuredLogger() {
        this(Map.of(), false);
    }

    /** Silent mode suppresses all output — useful for benchmarks and tests that measure performance. */
    public ConsoleStructuredLogger(boolean silent) {
        this(Map.of(), silent);
    }

    private ConsoleStructuredLogger(Map<String, Object> fields, boolean silent) {
        this.fields = new LinkedHashMap<>(fields);
        this.silent = silent;
    }

    @Override
    public StructuredLogger with(String key, Object value) {
        var next = new LinkedHashMap<>(fields);
        next.put(key, value);
        return new ConsoleStructuredLogger(next, silent);
    }

    @Override public void info(String message, Object... keyValues) { log("INFO", null, message, keyValues); }
    @Override public void warn(String message, Object... keyValues) { log("WARN", null, message, keyValues); }
    @Override public void error(Throwable error, String message, Object... keyValues) { log("ERROR", error, message, keyValues); }

    private void log(String level, Throwable error, String message, Object... keyValues) {
        if (silent) return;
        var all = new LinkedHashMap<>(fields);
        for (int i = 0; i + 1 < keyValues.length; i += 2) all.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        if (error != null) all.put("error", error.getClass().getSimpleName() + ":" + error.getMessage());
        System.out.println("log level=" + level + " at=" + Instant.now() + " message=\"" + message + "\" fields=" + all);
    }
}
