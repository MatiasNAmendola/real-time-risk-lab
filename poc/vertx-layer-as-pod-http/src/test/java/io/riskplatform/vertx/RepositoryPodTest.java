package io.riskplatform.vertx;

import io.riskplatform.vertx.common.PodSecurity;
import io.riskplatform.vertx.repository.RepositoryPod;
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
 * Smoke tests for the repository-pod.
 * The repository is fully self-contained (in-memory store) so all paths are testable in isolation.
 * Uses @BeforeAll to deploy once per class.
 */
@ExtendWith(VertxExtension.class)
@DisplayName("RepositoryPod smoke tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RepositoryPodTest {

    static Vertx vertx;
    static WebClient client;
    static int repositoryPort;

    @BeforeAll
    static void setUp() throws InterruptedException {
        vertx = Vertx.vertx();
        client = WebClient.create(vertx);
        repositoryPort = TestPorts.freePort();
        var latch = new CountDownLatch(1);
        vertx.deployVerticle(new RepositoryPod(repositoryPort))
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
    @DisplayName("GET /health returns 200 with pod=repository-pod and status=UP")
    void healthEndpointReturns200(VertxTestContext ctx) {
        client.get(repositoryPort, "localhost", "/health")
                .send()
                .onFailure(ctx::failNow)
                .onSuccess(res -> ctx.verify(() -> {
                    assertThat(res.statusCode()).isEqualTo(200);
                    var body = res.bodyAsJsonObject();
                    assertThat(body.getString("pod")).isEqualTo("repository-pod");
                    assertThat(body.getString("status")).isEqualTo("UP");
                    ctx.completeNow();
                }));
    }

    @Test
    @Order(2)
    @DisplayName("POST /internal/idempotency/find without token returns 403")
    void internalEndpointWithoutTokenReturns403(VertxTestContext ctx) {
        var payload = new JsonObject().put("idempotencyKey", "ik-test");

        client.post(repositoryPort, "localhost", "/internal/idempotency/find")
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
    @DisplayName("POST /internal/idempotency/find with correct token returns 200 with found=false for unknown key")
    void idempotencyFindUnknownKeyReturnsFalse(VertxTestContext ctx) {
        var payload = new JsonObject().put("idempotencyKey", "ik-not-exists");

        client.post(repositoryPort, "localhost", "/internal/idempotency/find")
                .putHeader("content-type", "application/json")
                .putHeader("x-pod-token", PodSecurity.USECASE_TO_REPOSITORY_TOKEN)
                .putHeader("x-pod-scopes", "repository:rw")
                .sendBuffer(Buffer.buffer(payload.encode()))
                .onFailure(ctx::failNow)
                .onSuccess(res -> ctx.verify(() -> {
                    assertThat(res.statusCode()).isEqualTo(200);
                    var body = res.bodyAsJsonObject();
                    assertThat(body.getBoolean("found")).isFalse();
                    ctx.completeNow();
                }));
    }
}
