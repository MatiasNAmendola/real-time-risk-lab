package com.naranjax.poc.risk.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.naranjax.poc.risk.client.config.ClientConfig;
import com.naranjax.poc.risk.client.http.JsonHttpClient;

/**
 * Test-only subclass that rewrites all URLs to point at a dynamically-assigned
 * server URL (e.g. from a Testcontainers-mapped port).
 *
 * Lives in the contract-test source set only — never shipped.
 */
final class ContractJsonHttpClient extends JsonHttpClient {

    private final String serverUrl;

    ContractJsonHttpClient(ClientConfig config, ObjectMapper mapper, String serverUrl) {
        super(config, mapper);
        this.serverUrl = serverUrl.replaceAll("/$", ""); // strip trailing slash
    }

    @Override
    public <T> T postJson(String url, Object body, Class<T> responseType) {
        return super.postJson(rewrite(url), body, responseType);
    }

    @Override
    public <T> T getJson(String url, Class<T> responseType) {
        return super.getJson(rewrite(url), responseType);
    }

    private String rewrite(String url) {
        int pathStart = url.indexOf('/', url.indexOf("://") + 3);
        String path = pathStart >= 0 ? url.substring(pathStart) : "/";
        return serverUrl + path;
    }
}
