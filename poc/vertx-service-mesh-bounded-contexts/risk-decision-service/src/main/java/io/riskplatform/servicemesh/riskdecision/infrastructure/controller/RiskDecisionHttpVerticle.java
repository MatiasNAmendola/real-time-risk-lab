package io.riskplatform.servicemesh.riskdecision.infrastructure.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.trace.Span;
import io.riskplatform.servicemesh.riskdecision.application.usecase.decision.DecisionPolicy;
import io.riskplatform.servicemesh.shared.EventBusAddresses;
import io.riskplatform.servicemesh.shared.FraudRulesResult;
import io.riskplatform.servicemesh.shared.MlScoreResult;
import io.riskplatform.servicemesh.shared.RiskRequest;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.List;
import java.util.UUID;

public final class RiskDecisionHttpVerticle extends AbstractVerticle {
    private static final Logger log = LoggerFactory.getLogger(RiskDecisionHttpVerticle.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final int PORT = 8090;
    private static final long SERVICE_TIMEOUT_MS = 120L;
    private final DecisionPolicy policy = new DecisionPolicy();

    @Override
    public void start(Promise<Void> startPromise) {
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        router.get("/healthz").handler(ctx -> ctx.response().end("OK"));
        router.post("/risk").handler(ctx -> {
            String correlationId = ctx.request().getHeader("X-Correlation-Id");
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = UUID.randomUUID().toString();
            }
            String corrId = correlationId;
            ctx.response().putHeader("X-Correlation-Id", corrId);
            MDC.put("correlationId", corrId);
            try {
                JsonObject body = ctx.body().asJsonObject();
                RiskRequest request = new RiskRequest(
                    body.getString("transactionId", UUID.randomUUID().toString()),
                    body.getString("customerId", "anonymous"),
                    body.getLong("amountCents", 0L),
                    corrId,
                    body.getBoolean("newDevice", false));
                evaluate(request)
                    .onSuccess(decision -> {
                        MDC.clear();
                        ctx.response().putHeader("Content-Type", "application/json").end(decision);
                    })
                    .onFailure(err -> {
                        log.error("risk decision failed correlationId={}", corrId, err);
                        MDC.clear();
                        ctx.response().setStatusCode(502).putHeader("Content-Type", "application/json")
                            .end(new JsonObject().put("error", err.getMessage()).put("correlationId", corrId).encode());
                    });
            } catch (Exception e) {
                log.error("invalid request correlationId={}", corrId, e);
                MDC.clear();
                ctx.response().setStatusCode(400).putHeader("Content-Type", "application/json")
                    .end(new JsonObject().put("error", "invalid request").put("correlationId", corrId).encode());
            }
        });
        vertx.createHttpServer().requestHandler(router).listen(PORT)
            .onSuccess(server -> { log.info("risk-decision-service listening port={}", PORT); startPromise.complete(); })
            .onFailure(startPromise::fail);
    }

    private Future<String> evaluate(RiskRequest request) throws Exception {
        String payload = mapper.writeValueAsString(request);
        DeliveryOptions opts = new DeliveryOptions().setSendTimeout(SERVICE_TIMEOUT_MS)
            .addHeader("correlationId", request.correlationId());
        Future<FraudRulesResult> rulesFuture = vertx.eventBus().<String>request(EventBusAddresses.FRAUD_RULES_EVALUATE, payload, opts)
            .map(reply -> read(reply.body(), FraudRulesResult.class));
        Future<MlScoreResult> mlFuture = vertx.eventBus().<String>request(EventBusAddresses.ML_SCORER_SCORE, payload, opts)
            .map(reply -> read(reply.body(), MlScoreResult.class))
            .recover(err -> Future.succeededFuture(new MlScoreResult(0.0, "fallback-timeout", true)));

        return rulesFuture.compose(rules -> mlFuture.map(ml -> policy.decide(request, rules, ml)))
            .compose(decision -> {
                Span.current().setAttribute("risk.decision", decision.decision());
                Span.current().setAttribute("risk.correlation_id", request.correlationId());
                String decisionJson;
                try {
                    decisionJson = mapper.writeValueAsString(decision);
                } catch (Exception e) {
                    return Future.failedFuture(e);
                }
                DeliveryOptions auditOpts = new DeliveryOptions().setSendTimeout(50L)
                    .addHeader("correlationId", request.correlationId());
                vertx.eventBus().request(EventBusAddresses.AUDIT_RECORD, decisionJson, auditOpts)
                    .onFailure(err -> log.warn("audit async failed correlationId={} error={}", request.correlationId(), err.getMessage()));
                return Future.succeededFuture(decisionJson);
            });
    }

    private <T> T read(String json, Class<T> type) {
        try { return mapper.readValue(json, type); }
        catch (Exception e) { throw new IllegalArgumentException("invalid " + type.getSimpleName(), e); }
    }
}
