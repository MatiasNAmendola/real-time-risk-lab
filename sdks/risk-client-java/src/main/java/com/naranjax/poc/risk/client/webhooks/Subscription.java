package com.naranjax.poc.risk.client.webhooks;

import java.time.Instant;

/**
 * Represents a registered webhook subscription.
 */
public record Subscription(
        String id,
        String callbackUrl,
        String eventFilter,
        Instant createdAt
) {}
