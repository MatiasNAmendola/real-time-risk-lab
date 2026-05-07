package com.naranjax.poc.risk.config;

/** Thrown when the config file does not exist at the specified path. */
public class ConfigNotFoundException extends RuntimeException {
    public ConfigNotFoundException(String path) {
        super("Config file not found: " + path);
    }
}
