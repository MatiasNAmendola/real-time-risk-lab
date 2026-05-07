package com.naranjax.poc.risk.client.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.naranjax.poc.risk.client.RiskClientException;
import com.naranjax.poc.risk.client.config.ClientConfig;
import com.naranjax.poc.risk.client.config.RetryPolicy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Thin wrapper around java.net.http.HttpClient with retry support.
 * All HTTP communication in the SDK goes through this class.
 */
public class JsonHttpClient {

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final ClientConfig config;

    public JsonHttpClient(ClientConfig config, ObjectMapper mapper) {
        this.config = config;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(config.timeout())
                .build();
    }

    public <T> T postJson(String url, Object body, Class<T> responseType) {
        try {
            byte[] requestBody = mapper.writeValueAsBytes(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(config.timeout())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("X-API-Key", config.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                    .build();

            return executeWithRetry(request, responseType);
        } catch (RiskClientException e) {
            throw e;
        } catch (Exception e) {
            throw new RiskClientException("POST " + url + " failed", e);
        }
    }

    public <T> T getJson(String url, Class<T> responseType) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(config.timeout())
                    .header("Accept", "application/json")
                    .header("X-API-Key", config.apiKey())
                    .GET()
                    .build();

            return executeWithRetry(request, responseType);
        } catch (RiskClientException e) {
            throw e;
        } catch (Exception e) {
            throw new RiskClientException("GET " + url + " failed", e);
        }
    }

    /** Opens a raw SSE connection; caller is responsible for consuming the InputStream. */
    public java.io.InputStream openSseStream(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "text/event-stream")
                    .header("Cache-Control", "no-cache")
                    .header("X-API-Key", config.apiKey())
                    .GET()
                    .build();

            HttpResponse<java.io.InputStream> resp = http.send(
                    request, HttpResponse.BodyHandlers.ofInputStream());

            if (resp.statusCode() != 200) {
                throw new RiskClientException("SSE stream returned HTTP " + resp.statusCode(), resp.statusCode());
            }
            return resp.body();
        } catch (RiskClientException e) {
            throw e;
        } catch (Exception e) {
            throw new RiskClientException("SSE stream failed", e);
        }
    }

    public HttpClient rawClient() { return http; }

    // -----------------------------------------------------------------------

    private <T> T executeWithRetry(HttpRequest request, Class<T> responseType) throws Exception {
        RetryPolicy policy = config.retryPolicy();
        int attempt = 0;
        long delayMs = policy.initialDelay().toMillis();

        while (true) {
            attempt++;
            try {
                HttpResponse<byte[]> resp = http.send(
                        request, HttpResponse.BodyHandlers.ofByteArray());

                if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                    if (responseType == Void.class) return null;
                    return mapper.readValue(resp.body(), responseType);
                }

                if (resp.statusCode() >= 500 && attempt < policy.maxAttempts()) {
                    Thread.sleep(delayMs);
                    delayMs = (long) (delayMs * policy.multiplier());
                    continue;
                }

                throw new RiskClientException(
                        "HTTP " + resp.statusCode() + " from " + request.uri(),
                        resp.statusCode());

            } catch (RiskClientException e) {
                throw e;
            } catch (java.net.http.HttpTimeoutException e) {
                if (attempt < policy.maxAttempts()) {
                    Thread.sleep(delayMs);
                    delayMs = (long) (delayMs * policy.multiplier());
                    continue;
                }
                throw e;
            }
        }
    }
}
