package com.naranjax.interview.vertx.repository;

import com.naranjax.interview.vertx.common.DTOs.*;
import com.naranjax.interview.vertx.common.*;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.naranjax.interview.vertx.common.PodSecurity.USECASE_TO_REPOSITORY_TOKEN;

public final class RepositoryPod extends AbstractVerticle {
    private final Map<String, RiskDecision> decisionsByTransaction = new ConcurrentHashMap<>();
    private final Map<String, RiskDecision> decisionsByIdempotency = new ConcurrentHashMap<>();
    private final Map<String, DecisionEvent> outbox = new ConcurrentHashMap<>();
    private final Map<String, Boolean> published = new ConcurrentHashMap<>();

    @Override
    public void start(Promise<Void> startPromise) {
        var router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        router.get("/health").handler(ctx -> HttpJson.ok(ctx, new Health("repository-pod", "UP")));

        router.route("/internal/*").handler(ctx -> PodSecurity.requireToken(ctx, USECASE_TO_REPOSITORY_TOKEN, "repository:rw"));

        router.post("/internal/idempotency/find").handler(ctx -> {
            var lookup = Json.decode(ctx.body().asString(), IdempotencyLookup.class);
            var decision = decisionsByIdempotency.get(lookup.idempotencyKey());
            HttpJson.ok(ctx, new MaybeDecision(decision != null, decision));
        });

        router.post("/internal/idempotency/save").handler(ctx -> {
            var save = Json.decode(ctx.body().asString(), IdempotencySave.class);
            decisionsByIdempotency.putIfAbsent(save.idempotencyKey(), save.decision());
            HttpJson.ok(ctx, new SavedDecision(decisionsByIdempotency.get(save.idempotencyKey())));
        });

        router.post("/internal/decisions").handler(ctx -> {
            var decision = Json.decode(ctx.body().asString(), RiskDecision.class);
            decisionsByTransaction.put(decision.transactionId(), decision);
            HttpJson.created(ctx, new SavedDecision(decision));
        });

        router.post("/internal/outbox").handler(ctx -> {
            var event = Json.decode(ctx.body().asString(), DecisionEvent.class);
            outbox.putIfAbsent(event.eventId(), event);
            published.putIfAbsent(event.eventId(), false);
            HttpJson.created(ctx, Map.of("eventId", event.eventId(), "status", "PENDING"));
        });

        router.get("/internal/outbox/pending").handler(ctx -> {
            var events = new ArrayList<DecisionEvent>();
            for (var entry : outbox.entrySet()) {
                if (!published.getOrDefault(entry.getKey(), false)) events.add(entry.getValue());
            }
            HttpJson.ok(ctx, new PendingEvents(events));
        });

        router.post("/internal/outbox/:eventId/published").handler(ctx -> {
            published.put(ctx.pathParam("eventId"), true);
            HttpJson.ok(ctx, Map.of("eventId", ctx.pathParam("eventId"), "status", "PUBLISHED"));
        });

        vertx.createHttpServer().requestHandler(router).listen(8082)
                .onSuccess(server -> {
                    System.out.println("repository-pod listening on " + server.actualPort());
                    startPromise.complete();
                })
                .onFailure(startPromise::fail);
    }
}
