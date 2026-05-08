package io.riskplatform.vertx.usecase;

import io.riskplatform.vertx.common.DTOs.*;
import io.riskplatform.vertx.common.*;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.BodyHandler;

import java.time.Instant;
import java.util.*;
import java.util.random.RandomGenerator;

import static io.riskplatform.vertx.common.PodSecurity.*;

public final class UsecasePod extends AbstractVerticle {
    private WebClient client;
    private final int usecasePort;
    private final int repositoryPort;
    private final RandomGenerator random = RandomGenerator.of("L64X128MixRandom");
    private int modelFailures;
    private long circuitOpenUntilNanos;

    public UsecasePod() {
        this(intEnv("USECASE_PORT", 8081), intEnv("REPOSITORY_PORT", 8082));
    }

    public UsecasePod(int usecasePort, int repositoryPort) {
        this.usecasePort = usecasePort;
        this.repositoryPort = repositoryPort;
    }

    @Override
    public void start(Promise<Void> startPromise) {
        client = WebClient.create(vertx);
        var router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        router.get("/health").handler(ctx -> HttpJson.ok(ctx, new Health("usecase-pod", "UP")));
        router.post("/internal/risk/evaluate")
                .handler(ctx -> PodSecurity.requireToken(ctx, CONTROLLER_TO_USECASE_TOKEN, "risk:evaluate"))
                .handler(ctx -> evaluate(Json.decode(ctx.body().asString(), RiskRequest.class))
                        .onSuccess(decision -> HttpJson.ok(ctx, decision))
                        .onFailure(err -> HttpJson.error(ctx, 500, "RISK_EVALUATION_FAILED", err.getMessage())));

        vertx.createHttpServer().requestHandler(router).listen(usecasePort)
                .onSuccess(server -> {
                    System.out.println("usecase-pod listening on " + server.actualPort());
                    startPromise.complete();
                })
                .onFailure(startPromise::fail);
    }

    private Future<RiskDecision> evaluate(RiskRequest request) {
        long started = System.nanoTime();
        return repo(HttpMethod.POST, "/internal/idempotency/find", new IdempotencyLookup(request.idempotencyKey()), MaybeDecision.class)
                .compose(found -> {
                    if (found.found()) return Future.succeededFuture(found.decision());
                    var draft = decide(request, started);
                    return repo(HttpMethod.POST, "/internal/idempotency/save", new IdempotencySave(request.idempotencyKey(), draft), SavedDecision.class)
                            .compose(saved -> repo(HttpMethod.POST, "/internal/decisions", saved.decision(), SavedDecision.class))
                            .compose(saved -> repo(HttpMethod.POST, "/internal/outbox", eventFrom(request, saved.decision()), Map.class)
                                    .map(saved.decision()));
                });
    }

    private RiskDecision decide(RiskRequest request, long startedNanos) {
        var rules = new ArrayList<String>();
        var fallbacks = new ArrayList<String>();
        Integer mlScore = null;
        String modelVersion = "not-called";
        String decision = "APPROVE";
        String reason = "low-risk";

        if (request.amountInCents() >= 50_000) {
            rules.add("high-amount-v1=true");
            decision = "REVIEW";
            reason = "amount-over-threshold";
        } else {
            rules.add("high-amount-v1=false");
            rules.add("new-device-young-customer-v1=false");
            if (System.nanoTime() < circuitOpenUntilNanos) {
                fallbacks.add("ml-circuit-open");
                decision = request.newDevice() || request.amountInCents() > 20_000 ? "REVIEW" : "APPROVE";
                reason = "ml-unavailable-fallback";
            } else {
                try {
                    var latency = 20 + random.nextLong(140);
                    if (latency > 110) throw new RuntimeException("ml-timeout simulatedLatencyMs=" + latency);
                    if (random.nextDouble() < 0.10) throw new RuntimeException("ml-temporary-error");
                    mlScore = Math.min(100, (int) (request.amountInCents() / 1_000) + (request.newDevice() ? 20 : 0));
                    modelVersion = "fraud-model-2026-05-07";
                    modelFailures = 0;
                    if (mlScore >= 85) { decision = "DECLINE"; reason = "ml-score-high"; }
                    else if (mlScore >= 60) { decision = "REVIEW"; reason = "ml-score-medium"; }
                } catch (RuntimeException ex) {
                    modelFailures++;
                    if (modelFailures >= 3) circuitOpenUntilNanos = System.nanoTime() + java.time.Duration.ofSeconds(3).toNanos();
                    fallbacks.add("ml-error=" + ex.getMessage());
                    decision = request.newDevice() || request.amountInCents() > 20_000 ? "REVIEW" : "APPROVE";
                    reason = "ml-error-fallback";
                }
            }
        }

        var elapsedMs = java.time.Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
        var trace = new DecisionTrace(request.correlationId(), "ruleset-2026-05-07", modelVersion, rules, fallbacks, mlScore);
        return new RiskDecision(request.transactionId(), decision, reason, elapsedMs, trace);
    }

    private DecisionEvent eventFrom(RiskRequest request, RiskDecision decision) {
        return new DecisionEvent(UUID.randomUUID().toString(), "risk.decision.evaluated", 1, Instant.now(),
                request.correlationId(), request.transactionId(), decision.decision(), decision.reason(),
                decision.trace().ruleSetVersion(), decision.trace().modelVersion());
    }

    private <T> Future<T> repo(HttpMethod method, String path, Object body, Class<T> responseType) {
        return client.request(method, repositoryPort, "localhost", path)
                .putHeader("content-type", "application/json")
                .putHeader("x-pod-token", USECASE_TO_REPOSITORY_TOKEN)
                .putHeader("x-pod-scopes", "repository:rw")
                .sendBuffer(io.vertx.core.buffer.Buffer.buffer(Json.encode(body)))
                .compose(res -> res.statusCode() >= 200 && res.statusCode() < 300
                        ? Future.succeededFuture(Json.decode(res.bodyAsString(), responseType))
                        : Future.failedFuture("repository status=" + res.statusCode() + " body=" + res.bodyAsString()));
    }

    private static int intEnv(String name, int fallback) {
        var value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : Integer.parseInt(value);
    }
}
