package com.naranjax.poc.risk.client.admin;

/**
 * Metadata about a single active fraud rule.
 */
public record RuleInfo(
        String id,
        String name,
        String description,
        boolean enabled,
        int priority
) {}
