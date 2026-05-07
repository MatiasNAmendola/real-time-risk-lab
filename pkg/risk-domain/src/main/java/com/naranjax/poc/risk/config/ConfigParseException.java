package com.naranjax.poc.risk.config;

/** Thrown when the YAML cannot be parsed (syntax error, wrong structure, etc.). */
public class ConfigParseException extends RuntimeException {
    public ConfigParseException(String path, Throwable cause) {
        super("Failed to parse config file: " + path + " — " + cause.getMessage(), cause);
    }
}
