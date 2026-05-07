package com.naranjax.interview.vertx;

import com.naranjax.interview.vertx.common.PodSecurity;
import com.naranjax.interview.vertx.repository.RepositoryPod;
import com.naranjax.interview.vertx.usecase.UsecasePod;
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
 * Token-based permission tests. Deploys usecase-pod (8081) + repository-pod (8082) once per class.
 *
 * Key invariant tested:
 *   - controller token (controller-risk-evaluate-token) is accepted by usecase, rejected by repository
 *   - usecase token (usecase-repository-rw-token) is accepted by repository
 */
@ExtendWith(VertxExtension.class)
@DisplayName("PodSecurity token-based permission tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PodSecurityTest {

    static Vertx vertx;
    static WebClient client;

    @BeforeAll
    static void setUp() throws InterruptedException {
        vertx = Vertx.vertx();
        client = WebClient.create(vertx);
        var latch = new CountDownLatch(1);
        vertx.deployVerticle(new RepositoryPod())
                .compose(__ -> vertx.deployVerticle(new UsecasePod()))
                .onSuccess(__ -> latch.countDown())
                .onFailure(err -> { throw new RuntimeException(err); });
        assertThat(latch.await(15, TimeUnit.SECONDS)).isTrue();
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
    @DisplayName("usecase: correct controller token + correct scope is accepted (not 403)")
    void usecaseAcceptsCorrectControllerToken(VertxTestContext ctx) {
        var payload = new JsonObject()
                .put("transactionId", "tx-sec-1")
                .put("customerId", "c-1")
                .put("amountInCents", 1000)
                .put("newDevice", false)
                .put("correlationId", "corr-sec-1")
                .put("idempotencyKey", "ik-sec-1");

        client.post(8081, "localhost", "/internal/risk/evaluate")
                .putHeader("content-type", "application/json")
                .putHeader("x-pod-token", PodSecurity.CONTROLLER_TO_USECASE_TOKEN)
                .putHeader("x-pod-scopes", "risk:evaluate")
                .sendBuffer(Buffer.buffer(payload.encode()))
                .onFailure(ctx::failNow)
                .onSuccess(res -> ctx.verify(() -> {
                    // Token accepted — result may be 200 (decision) or 500 (downstream issue), but NOT 403
                    assertThat(res.statusCode()).isNotEqualTo(403);
                    ctx.completeNow();
                }));
    }

    @Test
    @Order(2)
    @DisplayName("usecase: controller token with wrong scope is rejected 403")
    void usecaseRejectsCorrectTokenWithWrongScope(VertxTestContext ctx) {
        var payload = new JsonObject()
                .put("transactionId", "tx-sec-2")
                .put("customerId", "c-1")
                .put("amountInCents", 500)
                .put("newDevice", false)
                .put("correlationId", "corr-sec-2")
                .put("idempotencyKey", "ik-sec-2");

        client.post(8081, "localhost", "/internal/risk/evaluate")
                .putHeader("content-type", "application/json")
                .putHeader("x-pod-token", PodSecurity.CONTROLLER_TO_USECASE_TOKEN)
                .putHeader("x-pod-scopes", "repository:rw")  // wrong scope for usecase endpoint
                .sendBuffer(Buffer.buffer(payload.encode()))
                .onFailure(ctx::failNow)
                .onSuccess(res -> ctx.verify(() -> {
                    assertThat(res.statusCode()).isEqualTo(403);
                    ctx.completeNow();
                }));
    }

    @Test
    @Order(3)
    @DisplayName("repository: controller token is BLOCKED — least privilege enforced")
    void repositoryBlocksControllerToken(VertxTestContext ctx) {
        var payload = new JsonObject().put("idempotencyKey", "ik-sec-3");

        client.post(8082, "localhost", "/internal/idempotency/find")
                .putHeader("content-type", "application/json")
                .putHeader("x-pod-token", PodSecurity.CONTROLLER_TO_USECASE_TOKEN)
                .putHeader("x-pod-scopes", "risk:evaluate")
                .sendBuffer(Buffer.buffer(payload.encode()))
                .onFailure(ctx::failNow)
                .onSuccess(res -> ctx.verify(() -> {
                    assertThat(res.statusCode()).isEqualTo(403);
                    ctx.completeNow();
                }));
    }

    @Test
    @Order(4)
    @DisplayName("repository: usecase token + correct scope is accepted")
    void repositoryAcceptsUsecaseToken(VertxTestContext ctx) {
        var payload = new JsonObject().put("idempotencyKey", "ik-sec-4");

        client.post(8082, "localhost", "/internal/idempotency/find")
                .putHeader("content-type", "application/json")
                .putHeader("x-pod-token", PodSecurity.USECASE_TO_REPOSITORY_TOKEN)
                .putHeader("x-pod-scopes", "repository:rw")
                .sendBuffer(Buffer.buffer(payload.encode()))
                .onFailure(ctx::failNow)
                .onSuccess(res -> ctx.verify(() -> {
                    assertThat(res.statusCode()).isEqualTo(200);
                    ctx.completeNow();
                }));
    }
}
