package com.naranjax.interview.vertx.controller;

import com.naranjax.interview.vertx.common.DTOs.*;
import com.naranjax.interview.vertx.common.*;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.BodyHandler;

import static com.naranjax.interview.vertx.common.PodSecurity.CONTROLLER_TO_USECASE_TOKEN;

public final class ControllerPod extends AbstractVerticle {
    private WebClient client;

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
        router.get("/debug/try-repository").handler(ctx -> client.get(8082, "localhost", "/internal/outbox/pending")
                .putHeader("x-pod-token", "controller-has-no-repository-token")
                .putHeader("x-pod-scopes", "risk:evaluate")
                .send()
                .onSuccess(res -> ctx.response().setStatusCode(res.statusCode()).end(res.bodyAsString()))
                .onFailure(err -> HttpJson.error(ctx, 500, "DEBUG_CALL_FAILED", err.getMessage())));

        vertx.createHttpServer().requestHandler(router).listen(8080)
                .onSuccess(server -> {
                    System.out.println("controller-pod listening on " + server.actualPort());
                    startPromise.complete();
                })
                .onFailure(startPromise::fail);
    }

    private Future<RiskDecision> callUsecase(RiskRequest request) {
        return client.post(8081, "localhost", "/internal/risk/evaluate")
                .putHeader("content-type", "application/json")
                .putHeader("x-pod-token", CONTROLLER_TO_USECASE_TOKEN)
                .putHeader("x-pod-scopes", "risk:evaluate")
                .sendBuffer(Buffer.buffer(Json.encode(request)))
                .compose(res -> res.statusCode() >= 200 && res.statusCode() < 300
                        ? Future.succeededFuture(Json.decode(res.bodyAsString(), RiskDecision.class))
                        : Future.failedFuture("usecase status=" + res.statusCode() + " body=" + res.bodyAsString()));
    }
}
