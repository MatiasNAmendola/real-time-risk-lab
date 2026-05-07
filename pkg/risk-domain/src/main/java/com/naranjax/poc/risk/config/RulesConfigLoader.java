package com.naranjax.poc.risk.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Loads a rules.yaml file from disk, computes its content hash, and validates the schema.
 *
 * Loading is fail-fast: if the file is missing, unparseable, or invalid, an exception is thrown
 * and the previously active config is not replaced. The caller (HotReload or RuleEngineImpl)
 * is responsible for enforcing that invariant.
 */
public final class RulesConfigLoader {

    private static final ObjectMapper YAML = new YAMLMapper();
    private final RulesConfigValidator validator;

    public RulesConfigLoader() {
        this.validator = new RulesConfigValidator();
    }

    /**
     * Loads, parses, hashes, and validates a rules.yaml file.
     *
     * @param path filesystem path to the YAML file
     * @return validated RulesConfig with computed hash
     * @throws ConfigNotFoundException   if the file does not exist
     * @throws ConfigParseException      if the YAML cannot be parsed
     * @throws ConfigValidationException if the config fails schema validation
     */
    public RulesConfig load(String path) {
        return load(Path.of(path));
    }

    public RulesConfig load(Path path) {
        if (!Files.exists(path)) {
            throw new ConfigNotFoundException(path.toString());
        }

        byte[] bytes;
        try {
            bytes = Files.readAllBytes(path);
        } catch (IOException e) {
            throw new ConfigParseException(path.toString(), e);
        }

        RulesConfig config;
        try {
            config = YAML.readValue(bytes, RulesConfig.class);
        } catch (IOException e) {
            throw new ConfigParseException(path.toString(), e);
        }

        // Compute canonical SHA-256 of the file bytes (not the YAML object — byte identity)
        String hash = computeHash(bytes);
        config = config.withHash("sha256:" + hash);

        // Validate schema — throws ConfigValidationException if invalid
        validator.validate(config);

        return config;
    }

    private static String computeHash(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
