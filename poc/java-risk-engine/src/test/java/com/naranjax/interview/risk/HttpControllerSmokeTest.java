package com.naranjax.interview.risk;

import com.naranjax.interview.risk.config.RiskApplicationFactory;
import com.naranjax.interview.risk.infrastructure.controller.HttpController;
import com.naranjax.interview.risk.infrastructure.repository.log.ConsoleStructuredLogger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Smoke test for HttpController. Starts the server on a random port,
 * exercises all key scenarios using java.net.http.HttpClient (stdlib),
 * then stops the server.
 */
public final class HttpControllerSmokeTest {
    public static void main(String[] args) throws Exception {
        try (var app = new RiskApplicationFactory(true /* silent */)) {
            var logger = new ConsoleStructuredLogger(true);
            // Port 0 → OS picks a free port
            var controller = new HttpController(app.evaluateRiskUseCase(), logger, 0);
            controller.start();
            int port = controller.port();

            try {
                var client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .build();

                // 1. GET /healthz → 200
                var healthResp = get(client, port, "/healthz");
                assertStatus(healthResp, 200, "GET /healthz");
                assertContains(healthResp.body(), "ok", "GET /healthz body");

                // 2. GET /readyz → 200
                var readyResp = get(client, port, "/readyz");
                assertStatus(readyResp, 200, "GET /readyz");
                assertContains(readyResp.body(), "ready", "GET /readyz body");

                // 3. POST /risk low amount → 200 + APPROVE
                var approveResp = postJson(client, port, "/risk",
                        "{\"transactionId\":\"tx-smoke-1\",\"customerId\":\"c-1\",\"amountCents\":1000}");
                assertStatus(approveResp, 200, "POST /risk low amount");
                assertContains(approveResp.body(), "APPROVE", "POST /risk low amount decision");

                // 4. POST /risk high amount → 200 + DECLINE or REVIEW (not APPROVE)
                var declineResp = postJson(client, port, "/risk",
                        "{\"transactionId\":\"tx-smoke-2\",\"customerId\":\"c-2\",\"amountCents\":100000}");
                assertStatus(declineResp, 200, "POST /risk high amount");
                assertNotContains(declineResp.body(), "APPROVE", "POST /risk high amount should not APPROVE");

                // 5. POST /risk empty body → 400
                var badResp = postJson(client, port, "/risk", "");
                assertStatus(badResp, 400, "POST /risk empty body");

            } finally {
                controller.stop(0);
            }
        }
        System.out.println("HttpControllerSmokeTest OK");
    }

    // ------------------------------------------------------------------ helpers

    private static HttpResponse<String> get(HttpClient client, int port, String path) throws Exception {
        var req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> postJson(HttpClient client, int port, String path, String body) throws Exception {
        var req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(10))
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static void assertStatus(HttpResponse<String> resp, int expected, String label) {
        if (resp.statusCode() != expected) {
            throw new AssertionError(label + ": expected status " + expected + " but got " + resp.statusCode() + " body=" + resp.body());
        }
    }

    private static void assertContains(String body, String substring, String label) {
        if (!body.contains(substring)) {
            throw new AssertionError(label + ": expected body to contain \"" + substring + "\" but got: " + body);
        }
    }

    private static void assertNotContains(String body, String substring, String label) {
        if (body.contains(substring)) {
            throw new AssertionError(label + ": expected body NOT to contain \"" + substring + "\" but got: " + body);
        }
    }
}
