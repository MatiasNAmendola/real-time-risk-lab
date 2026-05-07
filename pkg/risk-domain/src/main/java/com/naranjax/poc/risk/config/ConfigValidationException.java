package com.naranjax.poc.risk.config;

import java.util.List;

/**
 * Thrown by RulesConfigValidator when a config fails validation.
 * Contains a structured list of errors so callers can report all problems at once.
 *
 * The active config is never replaced when this exception is thrown.
 */
public class ConfigValidationException extends RuntimeException {

    public record ValidationError(String rule, String field, String code, String message) {
        @Override
        public String toString() {
            return "[" + code + "] rule=" + rule + " field=" + field + ": " + message;
        }
    }

    private final List<ValidationError> errors;

    public ConfigValidationException(List<ValidationError> errors) {
        super("Config validation failed with " + errors.size() + " error(s): " + errors);
        this.errors = List.copyOf(errors);
    }

    public List<ValidationError> errors() {
        return errors;
    }
}
