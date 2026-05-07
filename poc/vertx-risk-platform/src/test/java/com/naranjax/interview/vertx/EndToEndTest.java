package com.naranjax.interview.vertx;

import com.naranjax.interview.vertx.controller.ControllerPod;
import com.naranjax.interview.vertx.repository.RepositoryPod;
import com.naranjax.interview.vertx.usecase.UsecasePod;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end: all 3 pods deployed in-process via a single Vertx instance.
 * Deploy order: repository (8082) -> usecase (8081) -> controller (8080).
 * Each test uses the already-started pods (one Vertx per class via @BeforeAll).
 */
@ExtendWith(VertxExtension.class)
@DisplayName("End-to-end: controller -> usecase -> repository")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EndToEndTest {

    static Vertx vertx;
    static WebClient client;

    @BeforeAll
    static void setUp() throws InterruptedException {
        vertx = Vertx.vertx();
        client = WebClient.create(vertx);
        var latch = new CountDownLatch(1);
        vertx.deployVerticle(new RepositoryPod())
                .compose(__ -> vertx.deployVerticle(new UsecasePod()))
                .compose(__ -> vertx.deployVerticle(new ControllerPod()))
                .onSuccess(__ -> latch.countDown())
                .onFailure(err -> { throw new RuntimeException(err); });
        assertThat(latch.await(20, TimeUnit.SECONDS)).isTrue();
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
    @DisplayName("POST /risk/evaluate low-amount returns decision with correct structure")
    void lowAmountReturnsParsableDecision(VertxTestContext ctx) {
        var payload = new JsonObject()
                .put("transactionId", "tx-e2e-low-1")
                .put("customerId", "c-1")
                .put("amountInCents", 1000)
                .put("newDevice", false)
                .put("correlationId", "corr-e2e-1")
                .put("idempotencyKey", "ik-e2e-low-1");

        client.post(8080, "localhost", "/risk/evaluate")
                .putHeader("content-type", "application/json")
                .sendJsonObject(payload)
                .onFailure(ctx::failNow)
                .onSuccess(res -> ctx.verify(() -> {
                    assertThat(res.statusCode()).isEqualTo(200);
                    var body = res.bodyAsJsonObject();
                    assertThat(body.getString("transactionId")).isEqualTo("tx-e2e-low-1");
                    assertThat(body.getString("decision")).isIn("APPROVE", "REVIEW", "DECLINE");
                    assertThat(body.getString("reason")).isNotBlank();
                    assertThat(body.getLong("elapsedMs")).isGreaterThanOrEqualTo(0L);
                    assertThat(body.getJsonObject("trace")).isNotNull();
                    assertThat(body.getJsonObject("trace").getString("correlationId"))
                            .isEqualTo("corr-e2e-1");
                    ctx.completeNow();
                }));
    }

    @Test
    @Order(2)
    @DisplayName("POST /risk/evaluate high-amount (500000 cents) returns REVIEW (amount-over-threshold)")
    void highAmountReturnsReview(VertxTestContext ctx) {
        var payload = new JsonObject()
                .put("transactionId", "tx-e2e-high-1")
                .put("customerId", "c-1")
                .put("amountInCents", 500_000)
                .put("newDevice", false)
                .put("correlationId", "corr-e2e-2")
                .put("idempotencyKey", "ik-e2e-high-1");

        client.post(8080, "localhost", "/risk/evaluate")
                .putHeader("content-type", "application/json")
                .sendJsonObject(payload)
                .onFailure(ctx::failNow)
                .onSuccess(res -> ctx.verify(() -> {
                    assertThat(res.statusCode()).isEqualTo(200);
                    var body = res.bodyAsJsonObject();
                    assertThat(body.getString("decision")).isEqualTo("REVIEW");
                    assertThat(body.getString("reason")).isEqualTo("amount-over-threshold");
                    ctx.completeNow();
                }));
    }

    @Test
    @Order(3)
    @DisplayName("Idempotency: same key returns same decision on second call")
    void idempotencyReturnsSameDecisionOnRepeat(VertxTestContext ctx) {
        var payload = new JsonObject()
                .put("transactionId", "tx-e2e-idem-1")
                .put("customerId", "c-1")
                .put("amountInCents", 2000)
                .put("newDevice", false)
                .put("correlationId", "corr-e2e-idem")
                .put("idempotencyKey", "ik-e2e-idem-unique");

        Checkpoint bothDone = ctx.checkpoint(2);
        final String[] firstDecision = {null};

        client.post(8080, "localhost", "/risk/evaluate")
                .putHeader("content-type", "application/json")
                .sendJsonObject(payload)
                .onFailure(ctx::failNow)
                .onSuccess(first -> {
                    ctx.verify(() -> {
                        assertThat(first.statusCode()).isEqualTo(200);
                        firstDecision[0] = first.bodyAsJsonObject().getString("decision");
                    });
                    bothDone.flag();

                    client.post(8080, "localhost", "/risk/evaluate")
                            .putHeader("content-type", "application/json")
                            .sendJsonObject(payload)
                            .onFailure(ctx::failNow)
                            .onSuccess(second -> ctx.verify(() -> {
                                assertThat(second.statusCode()).isEqualTo(200);
                                assertThat(second.bodyAsJsonObject().getString("decision"))
                                        .isEqualTo(firstDecision[0]);
                                bothDone.flag();
                            }));
                });
    }
}
