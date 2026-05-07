package com.naranjax.poc.risk.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.naranjax.poc.risk.client.config.ClientConfig;
import com.naranjax.poc.risk.client.http.JsonHttpClient;

/**
 * Test-only subclass of JsonHttpClient that overrides the base URL so integration
 * tests can point at a Testcontainers-mapped port instead of the hard-coded
 * Environment.LOCAL coordinates.
 *
 * This class lives in the integrationTest source set and is never shipped.
 */
final class IntegrationJsonHttpClient extends JsonHttpClient {

    private final String baseUrl;

    IntegrationJsonHttpClient(ClientConfig config, ObjectMapper mapper, String baseUrl) {
        super(config, mapper);
        this.baseUrl = baseUrl;
    }

    @Override
    public <T> T postJson(String url, Object body, Class<T> responseType) {
        return super.postJson(rewrite(url), body, responseType);
    }

    @Override
    public <T> T getJson(String url, Class<T> responseType) {
        return super.getJson(rewrite(url), responseType);
    }

    @Override
    public java.io.InputStream openSseStream(String url) {
        return super.openSseStream(rewrite(url));
    }

    /**
     * Replaces the scheme+host+port prefix with the container base URL while
     * preserving the path and query string.
     */
    private String rewrite(String url) {
        // url looks like "http://localhost:8080/risk" — strip the origin part
        int pathStart = url.indexOf('/', url.indexOf("://") + 3);
        String path = pathStart >= 0 ? url.substring(pathStart) : "/";
        return baseUrl + path;
    }
}
