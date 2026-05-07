package com.naranjax.interview.vertx;

import com.naranjax.interview.vertx.controller.ControllerPod;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke tests for the controller-pod in isolation.
 * The controller starts normally; calls to usecase (port 8081) will fail with 502 — expected.
 * Uses @BeforeAll / @AfterAll to deploy the verticle once per class, avoiding port conflicts
 * between @BeforeEach restarts.
 */
@ExtendWith(VertxExtension.class)
@DisplayName("ControllerPod smoke tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ControllerPodTest {

    static Vertx vertx;
    static WebClient client;

    @BeforeAll
    static void setUp() throws InterruptedException {
        vertx = Vertx.vertx();
        client = WebClient.create(vertx);
        var latch = new CountDownLatch(1);
        vertx.deployVerticle(new ControllerPod())
                .onSuccess(__ -> latch.countDown())
                .onFailure(err -> { throw new RuntimeException(err); });
        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
    }

    @AfterAll
    static void tearDown() throws InterruptedException {
        var latch = new CountDownLatch(1);
        client.close();
        vertx.close().onComplete(__ -> latch.countDown());
        latch.await(10, TimeUnit.SECONDS);
    }

    @Test
    @Order(1)
    @DisplayName("GET /health returns 200 with pod=controller-pod and status=UP")
    void healthEndpointReturns200(VertxTestContext ctx) {
        client.get(8080, "localhost", "/health")
                .send()
                .onFailure(ctx::failNow)
                .onSuccess(res -> ctx.verify(() -> {
                    assertThat(res.statusCode()).isEqualTo(200);
                    var body = res.bodyAsJsonObject();
                    assertThat(body.getString("pod")).isEqualTo("controller-pod");
                    assertThat(body.getString("status")).isEqualTo("UP");
                    ctx.completeNow();
                }));
    }

    @Test
    @Order(2)
    @DisplayName("POST /risk/evaluate with no downstream returns 502 (usecase unavailable)")
    void riskEvaluateWithoutDownstreamReturns502(VertxTestContext ctx) {
        var payload = new JsonObject()
                .put("transactionId", "tx-smoke-1")
                .put("customerId", "c-1")
                .put("amountInCents", 1000)
                .put("newDevice", false)
                .put("correlationId", "corr-1")
                .put("idempotencyKey", "ik-1");

        client.post(8080, "localhost", "/risk/evaluate")
                .putHeader("content-type", "application/json")
                .sendJsonObject(payload)
                .onFailure(ctx::failNow)
                .onSuccess(res -> ctx.verify(() -> {
                    assertThat(res.statusCode()).isEqualTo(502);
                    ctx.completeNow();
                }));
    }
}
