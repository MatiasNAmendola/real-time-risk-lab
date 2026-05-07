package com.naranjax.poc.risk.client.webhooks;

import com.naranjax.poc.risk.client.http.JsonHttpClient;
import com.naranjax.poc.risk.client.config.ClientConfig;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Webhook management channel. Mirrors the server-side webhook registry API.
 */
public final class WebhooksClient {

    private final JsonHttpClient http;
    private final String baseUrl;

    public WebhooksClient(ClientConfig config, JsonHttpClient http) {
        this.http    = http;
        this.baseUrl = config.environment().restBaseUrl();
    }

    /**
     * Register a callback URL for the given event filter (e.g. "DECLINE,REVIEW").
     */
    public Subscription subscribe(String callbackUrl, String eventFilter) {
        var body = Map.of(
                "callbackUrl", callbackUrl,
                "events", Arrays.asList(eventFilter.split(",")));
        return http.postJson(baseUrl + "/webhook/register", body, Subscription.class);
    }

    /** Unregister a subscription by its ID. */
    public void unsubscribe(String subscriptionId) {
        http.postJson(baseUrl + "/webhook/unregister/" + subscriptionId,
                Map.of(), Void.class);
    }

    /** List all subscriptions registered for this API key. */
    public List<Subscription> list() {
        Subscription[] arr = http.getJson(baseUrl + "/webhook/subscriptions", Subscription[].class);
        return Arrays.asList(arr);
    }

    /**
     * Verify an inbound webhook payload against its HMAC-SHA256 signature.
     * Returns true if the signature is valid; false otherwise.
     */
    public boolean verify(byte[] payload, String signature, String signingSecret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(payload);
            String expectedHex = HexFormat.of().formatHex(expected);
            // constant-time comparison
            return MessageDigestCompare.constantTimeEquals(expectedHex, signature);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 not available", e);
        }
    }

    private static final class MessageDigestCompare {
        static boolean constantTimeEquals(String a, String b) {
            if (a.length() != b.length()) return false;
            int result = 0;
            for (int i = 0; i < a.length(); i++) {
                result |= a.charAt(i) ^ b.charAt(i);
            }
            return result == 0;
        }
    }
}
