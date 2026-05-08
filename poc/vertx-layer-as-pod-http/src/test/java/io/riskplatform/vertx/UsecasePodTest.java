package io.riskplatform.vertx;

import io.riskplatform.vertx.common.PodSecurity;
import io.riskplatform.vertx.usecase.UsecasePod;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
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
 * Smoke tests for the usecase-pod in isolation.
 * Uses @BeforeAll to deploy once, avoiding port-binding conflicts between test methods.
 */
@ExtendWith(VertxExtension.class)
@DisplayName("UsecasePod smoke tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UsecasePodTest {

    static Vertx vertx;
    static WebClient client;
    static int usecasePort;
    static int repositoryPort;

    @BeforeAll
    static void setUp() throws InterruptedException {
        vertx = Vertx.vertx();
        client = WebClient.create(vertx);
        usecasePort = TestPorts.freePort();
        repositoryPort = TestPorts.freePort();
        var latch = new CountDownLatch(1);
        vertx.deployVerticle(new UsecasePod(usecasePort, repositoryPort))
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
    @DisplayName("GET /health returns 200 with pod=usecase-pod and status=UP")
    void healthEndpointReturns200(VertxTestContext ctx) {
        client.get(usecasePort, "localhost", "/health")
                .send()
                .onFailure(ctx::failNow)
                .onSuccess(res -> ctx.verify(() -> {
                    assertThat(res.statusCode()).isEqualTo(200);
                    var body = res.bodyAsJsonObject();
                    assertThat(body.getString("pod")).isEqualTo("usecase-pod");
                    assertThat(body.getString("status")).isEqualTo("UP");
                    ctx.completeNow();
                }));
    }

    @Test
    @Order(2)
    @DisplayName("POST /internal/risk/evaluate without token returns 403")
    void evaluateWithoutTokenReturns403(VertxTestContext ctx) {
        var payload = new JsonObject()
                .put("transactionId", "tx-1")
                .put("customerId", "c-1")
                .put("amountInCents", 1000)
                .put("newDevice", false)
                .put("correlationId", "corr-1")
                .put("idempotencyKey", "ik-usecase-403");

        client.post(usecasePort, "localhost", "/internal/risk/evaluate")
                .putHeader("content-type", "application/json")
                .sendBuffer(Buffer.buffer(payload.encode()))
                .onFailure(ctx::failNow)
                .onSuccess(res -> ctx.verify(() -> {
                    assertThat(res.statusCode()).isEqualTo(403);
                    ctx.completeNow();
                }));
    }

    @Test
    @Order(3)
    @DisplayName("POST /internal/risk/evaluate with wrong token returns 403")
    void evaluateWithWrongTokenReturns403(VertxTestContext ctx) {
        var payload = new JsonObject()
                .put("transactionId", "tx-1")
                .put("customerId", "c-1")
                .put("amountInCents", 1000)
                .put("newDevice", false)
                .put("correlationId", "corr-1")
                .put("idempotencyKey", "ik-usecase-wrong");

        client.post(usecasePort, "localhost", "/internal/risk/evaluate")
                .putHeader("content-type", "application/json")
                .putHeader("x-pod-token", "wrong-token")
                .putHeader("x-pod-scopes", "risk:evaluate")
                .sendBuffer(Buffer.buffer(payload.encode()))
                .onFailure(ctx::failNow)
                .onSuccess(res -> ctx.verify(() -> {
                    assertThat(res.statusCode()).isEqualTo(403);
                    ctx.completeNow();
                }));
    }
}
