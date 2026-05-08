package io.riskplatform.vertx.controller;

import io.riskplatform.vertx.common.DTOs.*;
import io.riskplatform.vertx.common.*;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.BodyHandler;

import static io.riskplatform.vertx.common.PodSecurity.CONTROLLER_TO_USECASE_TOKEN;

public final class ControllerPod extends AbstractVerticle {
    private WebClient client;
    private final int controllerPort;
    private final int usecasePort;
    private final int repositoryPort;

    public ControllerPod() {
        this(intEnv("CONTROLLER_PORT", 8080), intEnv("USECASE_PORT", 8081), intEnv("REPOSITORY_PORT", 8082));
    }

    public ControllerPod(int controllerPort, int usecasePort, int repositoryPort) {
        this.controllerPort = controllerPort;
        this.usecasePort = usecasePort;
        this.repositoryPort = repositoryPort;
    }

    @Override
    public void start(Promise<Void> startPromise) {
        client = WebClient.create(vertx);
        var router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        router.get("/health").handler(ctx -> HttpJson.ok(ctx, new Health("controller-pod", "UP")));

        router.post("/risk/evaluate").handler(ctx -> {
            var request = Json.decode(ctx.body().asString(), RiskRequest.class);
            callUsecase(request)
                    .onSuccess(decision -> HttpJson.ok(ctx, decision))
                    .onFailure(err -> HttpJson.error(ctx, 502, "USECASE_UNAVAILABLE", err.getMessage()));
        });

        // Deliberately demonstrates least privilege: controller does not own repository token.
        router.get("/debug/try-repository").handler(ctx -> client.get(repositoryPort, "localhost", "/internal/outbox/pending")
                .putHeader("x-pod-token", System.getenv().getOrDefault("CONTROLLER_REPOSITORY_DENY_TOKEN", "change-me-controller-deny-token"))
                .putHeader("x-pod-scopes", "risk:evaluate")
                .send()
                .onSuccess(res -> ctx.response().setStatusCode(res.statusCode()).end(res.bodyAsString()))
                .onFailure(err -> HttpJson.error(ctx, 500, "DEBUG_CALL_FAILED", err.getMessage())));

        vertx.createHttpServer().requestHandler(router).listen(controllerPort)
                .onSuccess(server -> {
                    System.out.println("controller-pod listening on " + server.actualPort());
                    startPromise.complete();
                })
                .onFailure(startPromise::fail);
    }

    private Future<RiskDecision> callUsecase(RiskRequest request) {
        return client.post(usecasePort, "localhost", "/internal/risk/evaluate")
                .putHeader("content-type", "application/json")
                .putHeader("x-pod-token", CONTROLLER_TO_USECASE_TOKEN)
                .putHeader("x-pod-scopes", "risk:evaluate")
                .sendBuffer(Buffer.buffer(Json.encode(request)))
                .compose(res -> res.statusCode() >= 200 && res.statusCode() < 300
                        ? Future.succeededFuture(Json.decode(res.bodyAsString(), RiskDecision.class))
                        : Future.failedFuture("usecase status=" + res.statusCode() + " body=" + res.bodyAsString()));
    }

    private static int intEnv(String name, int fallback) {
        var value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : Integer.parseInt(value);
    }
}
