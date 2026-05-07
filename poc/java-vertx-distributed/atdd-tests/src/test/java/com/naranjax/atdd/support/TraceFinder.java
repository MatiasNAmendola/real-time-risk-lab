package com.naranjax.atdd.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Queries the OpenObserve API to find spans for a given traceId.
 *
 * <p>Usage in a .feature file:
 * <pre>
 *   * def TraceFinder = Java.type('com.naranjax.atdd.support.TraceFinder')
 *   * def spans = TraceFinder.findSpans(openObserveUrl, traceId, 5000)
 *   * def serviceNames = TraceFinder.serviceNames(spans)
 *   * match serviceNames contains 'controller-app'
 *   * match serviceNames contains 'usecase-app'
 *   * match serviceNames contains 'repository-app'
 * </pre>
 */
public class TraceFinder {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    // Default credentials for the OpenObserve instance defined in docker-compose.yml
    private static final String OO_USER = "admin@example.com";
    private static final String OO_PASS = "Complexpass#";

    private TraceFinder() {}

    /**
     * Polls OpenObserve until spans for {@code traceId} are available or the timeout elapses.
     *
     * @param openObserveUrl base URL, e.g. {@code http://localhost:5080}
     * @param traceId        the W3C traceId (32 hex chars)
     * @param timeoutMs      maximum wait in milliseconds
     * @return list of span objects (each represented as {@code Map<String,Object>})
     * @throws AssertionError if no spans are found within the timeout
     */
    public static List<Map<String, Object>> findSpans(
            String openObserveUrl, String traceId, long timeoutMs) {

        long deadline = System.currentTimeMillis() + timeoutMs;
        String auth = Base64.getEncoder()
                .encodeToString((OO_USER + ":" + OO_PASS).getBytes());
        String url = openObserveUrl + "/api/default/_search?type=traces&trace_id=" + traceId;

        while (System.currentTimeMillis() < deadline) {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", "Basic " + auth)
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(3))
                        .GET()
                        .build();

                HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());

                if (resp.statusCode() == 200) {
                    JsonNode root = MAPPER.readTree(resp.body());
                    JsonNode hits = root.path("hits");
                    if (hits.isArray() && hits.size() > 0) {
                        List<Map<String, Object>> spans = new java.util.ArrayList<>();
                        hits.forEach(node -> spans.add(MAPPER.convertValue(node, Map.class)));
                        return spans;
                    }
                }

                Thread.sleep(500);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // transient network error — keep polling
            }
        }

        throw new AssertionError(
                "No spans found in OpenObserve for traceId=" + traceId + " within " + timeoutMs + "ms");
    }

    /**
     * Extracts the distinct service names from a list of spans.
     */
    @SuppressWarnings("unchecked")
    public static List<String> serviceNames(List<Map<String, Object>> spans) {
        return spans.stream()
                .map(s -> {
                    Object res = s.get("resource");
                    if (res instanceof Map) {
                        Object svcName = ((Map<String, Object>) res).get("service.name");
                        if (svcName != null) return svcName.toString();
                    }
                    // fallback: top-level service_name field
                    Object top = s.get("service_name");
                    return top != null ? top.toString() : "unknown";
                })
                .distinct()
                .collect(Collectors.toList());
    }
}
