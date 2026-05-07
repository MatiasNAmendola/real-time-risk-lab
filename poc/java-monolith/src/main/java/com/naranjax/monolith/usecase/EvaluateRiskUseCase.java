package com.naranjax.monolith.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.naranjax.distributed.shared.FeatureSnapshot;
import com.naranjax.distributed.shared.RiskDecision;
import com.naranjax.distributed.shared.RiskRequest;
import com.naranjax.distributed.shared.resilience.LatencyBudget;
import com.naranjax.distributed.shared.resilience.SimpleCircuitBreaker;
import com.naranjax.distributed.shared.rules.FraudRule;
import com.naranjax.distributed.shared.rules.HighAmountRule;
import com.naranjax.distributed.shared.rules.NewDeviceYoungCustomerRule;
import com.naranjax.distributed.shared.rules.RuleEvaluation;
import com.naranjax.monolith.repository.KafkaDecisionPublisher;
import com.naranjax.monolith.repository.PostgresFeatureRepository;
import com.naranjax.monolith.repository.S3AuditPublisher;
import com.naranjax.monolith.repository.SqsDecisionPublisher;
import com.naranjax.monolith.repository.ValkeyIdempotencyStore;
import com.naranjax.poc.risk.audit.RulesAuditTrail;
import com.naranjax.poc.risk.config.RulesConfigLoader;
import com.naranjax.poc.risk.engine.AggregateDecision;
import com.naranjax.poc.risk.engine.RuleEngine;
import com.naranjax.poc.risk.engine.RuleEngineImpl;
import io.opentelemetry.api.trace.Span;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Use-case verticle for the monolith: evaluates transaction risk end-to-end
 * using LOCAL (in-process) calls to the repository layer.
 *
 * <p>Key architectural difference from java-vertx-distributed:
 * <ul>
 *   <li>Distributed: calls repository via Hazelcast event bus (network hop).
 *   <li>Monolith: calls repository directly (in-process method call, zero network cost).
 * </ul>
 *
 * <p>Functional parity is identical: same rules, same policy, same outputs.
 */
public class EvaluateRiskUseCase extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(EvaluateRiskUseCase.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long BUDGET_MS  = 280L;
    private static final String KAFKA_TOPIC = "risk-decisions";

    private static final List<FraudRule> LEGACY_RULES = List.of(
        new HighAmountRule(),
        new NewDeviceYoungCustomerRule()
    );

    private RuleEngine ruleEngine;
    private RulesAuditTrail rulesAuditTrail;

    private final SimpleCircuitBreaker mlBreaker = new SimpleCircuitBreaker(3, 30_000L);

    // Repository adapters injected via constructor (manual DI, no framework)
    private final PostgresFeatureRepository featureRepository;
    private final ValkeyIdempotencyStore    idempotencyStore;
    private final KafkaDecisionPublisher    kafkaPublisher;
    private final S3AuditPublisher          s3Publisher;
    private final SqsDecisionPublisher      sqsPublisher;

    public EvaluateRiskUseCase(
            PostgresFeatureRepository featureRepository,
            ValkeyIdempotencyStore    idempotencyStore,
            KafkaDecisionPublisher    kafkaPublisher,
            S3AuditPublisher          s3Publisher,
            SqsDecisionPublisher      sqsPublisher) {
        this.featureRepository = featureRepository;
        this.idempotencyStore  = idempotencyStore;
        this.kafkaPublisher    = kafkaPublisher;
        this.s3Publisher       = s3Publisher;
        this.sqsPublisher      = sqsPublisher;
    }

    @Override
    public void start(Promise<Void> startPromise) {
        rulesAuditTrail = new RulesAuditTrail();
        String rulesConfigPath = System.getenv().getOrDefault(
                "RULES_CONFIG_PATH", "examples/rules-config/v1/rules.yaml");
        try {
            var config = new RulesConfigLoader().load(rulesConfigPath);
            ruleEngine = new RuleEngineImpl(config, rulesAuditTrail);
            log.info("[monolith] Rules engine loaded: version={} hash={}", config.version(), config.hash());
        } catch (Exception e) {
            log.warn("[monolith] Rules config not found, using legacy rules: {}", e.getMessage());
            ruleEngine = null;
        }

        // Listen for hot reload events (from admin API reload endpoint)
        vertx.eventBus().<String>consumer("rules.reload", msg -> {
            try {
                var config = new RulesConfigLoader().load(rulesConfigPath);
                ruleEngine = new RuleEngineImpl(config, rulesAuditTrail);
                log.info("[monolith] Rules hot-reloaded: hash={}", msg.body());
            } catch (Exception e) {
                log.error("[monolith] Hot reload failed: {}", e.getMessage());
            }
        });

        vertx.eventBus().<String>consumer(MonolithEventBusAddress.USECASE_EVALUATE, this::handleEvaluate)
            .completion()
            .onSuccess(v -> {
                log.info("[monolith] EvaluateRiskUseCase ready");
                startPromise.complete();
            })
            .onFailure(startPromise::fail);
    }

    private void handleEvaluate(Message<String> msg) {
        String correlationId = msg.headers().get("correlationId");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        final String corrId = correlationId;
        MDC.put("correlationId", corrId);

        RiskRequest req;
        try {
            req = MAPPER.readValue(msg.body(), RiskRequest.class);
        } catch (Exception e) {
            MDC.clear();
            msg.fail(400, "invalid request: " + e.getMessage());
            return;
        }

        final RiskRequest finalReq = req.correlationId() == null
            ? new RiskRequest(req.transactionId(), req.customerId(), req.amountCents(),
                corrId, req.idempotencyKey(), req.newDevice())
            : req;

        // Idempotency check (in-process call to ValkeyIdempotencyStore)
        checkIdempotency(finalReq)
            .onSuccess(cached -> {
                if (cached != null) {
                    log.info("[monolith] idempotency hit key={}", finalReq.idempotencyKey());
                    Span.current().setAttribute("risk.idempotency_hit", true);
                    msg.reply(cached);
                    MDC.clear();
                    return;
                }
                continueEvaluation(msg, finalReq, corrId);
            })
            .onFailure(err -> {
                log.warn("[monolith] idempotency check failed, continuing: {}", err.getMessage());
                continueEvaluation(msg, finalReq, corrId);
            });
    }

    private void continueEvaluation(Message<String> msg, RiskRequest req, String corrId) {
        LatencyBudget budget = new LatencyBudget(BUDGET_MS);

        // In-process repository call (no event bus hop — monolith advantage)
        vertx.executeBlocking(() -> featureRepository.findByCustomerId(req.customerId()))
            .onSuccess(snapshot -> {
                List<RuleEvaluation> ruleResults;
                if (ruleEngine != null) {
                    com.naranjax.poc.risk.engine.FeatureSnapshot snap =
                        com.naranjax.poc.risk.engine.FeatureSnapshot.builder()
                            .customerId(req.customerId())
                            .transactionId(req.transactionId())
                            .amountCents(req.amountCents())
                            .newDevice(req.newDevice())
                            .transactionCount10m(0)
                            .chargebackCount90d(0)
                            .build();
                    AggregateDecision engineDecision = ruleEngine.evaluate(snap);
                    ruleResults = engineDecision.ruleResults().stream()
                        .filter(com.naranjax.poc.risk.rule.RuleEvaluation::triggered)
                        .map(re -> new RuleEvaluation(true, re.reason(), re.weight()))
                        .toList();
                } else {
                    ruleResults = LEGACY_RULES.stream()
                        .map(r -> r.evaluate(req, snapshot))
                        .toList();
                }

                double mlScore = snapshot.riskScore();
                boolean fallbackApplied = false;

                if (budget.canSpend(80L) && mlBreaker.allowRequest()) {
                    try {
                        mlScore = snapshot.riskScore(); // inline fake scorer
                        mlBreaker.recordSuccess();
                    } catch (Exception e) {
                        mlBreaker.recordFailure();
                        fallbackApplied = true;
                    }
                } else {
                    fallbackApplied = true;
                }

                RiskDecision decision = applyPolicy(ruleResults, mlScore, corrId,
                    req.transactionId(), fallbackApplied);

                Span span = Span.current();
                span.setAttribute("risk.decision",           decision.decision());
                span.setAttribute("risk.ml.score",           mlScore);
                span.setAttribute("risk.budget.remaining_ms", budget.remainingMillis());
                span.setAttribute("risk.fallback_applied",   fallbackApplied);
                span.setAttribute("risk.amount_cents",       req.amountCents());
                span.setAttribute("risk.correlation_id",     corrId);
                span.setAttribute("risk.cb.state",           mlBreaker.currentState().name());

                log.info("[monolith] decision={} amount={} mlScore={} corrId={}",
                    decision.decision(), req.amountCents(), mlScore, corrId);

                JsonObject broadcastPayload = new JsonObject()
                    .put("decision",       decision.decision())
                    .put("reason",         decision.reason())
                    .put("correlationId",  corrId)
                    .put("transactionId",  req.transactionId())
                    .put("fallback",       fallbackApplied)
                    .put("decisionSource", fallbackApplied ? "rules" : "ml");
                vertx.eventBus().publish(MonolithEventBusAddress.RISK_DECISION_BROADCAST, broadcastPayload.encode());

                // Async outputs (fire and forget)
                kafkaPublisher.publish(req, decision, corrId, KAFKA_TOPIC);
                s3Publisher.publishAudit(req, decision, corrId)
                    .onFailure(e -> log.error("[monolith] S3 audit failed: {}", e.getMessage()));
                sqsPublisher.publish(req, decision, corrId)
                    .onFailure(e -> log.error("[monolith] SQS publish failed: {}", e.getMessage()));

                String decisionJson;
                try {
                    decisionJson = MAPPER.writeValueAsString(decision);
                } catch (Exception e) {
                    MDC.clear();
                    msg.fail(500, "serialization error: " + e.getMessage());
                    return;
                }

                storeIdempotency(req, decisionJson)
                    .onComplete(ar -> {
                        msg.reply(decisionJson);
                        MDC.clear();
                    });
            })
            .onFailure(err -> {
                log.error("[monolith] feature repository error: {}", err.getMessage());
                MDC.clear();
                msg.fail(502, "repository error: " + err.getMessage());
            });
    }

    private RiskDecision applyPolicy(List<RuleEvaluation> results, double mlScore,
                                     String corrId, String txId, boolean fallback) {
        boolean highAmountFired = !results.isEmpty() && results.get(0).triggered();
        boolean newDeviceFired  = results.size() > 1 && results.get(1).triggered();

        if (highAmountFired) return new RiskDecision("DECLINE",
            "HighAmountRule v1: " + results.get(0).reason(), corrId);
        if (newDeviceFired)  return new RiskDecision("REVIEW",
            "NewDeviceYoungCustomerRule v1: " + results.get(1).reason(), corrId);
        if (mlScore > 0.7)   return new RiskDecision("DECLINE",
            "ml score " + mlScore + " > 0.7", corrId);
        if (mlScore > 0.4)   return new RiskDecision("REVIEW",
            "ml score " + mlScore + " > 0.4", corrId);
        return new RiskDecision("APPROVE", "transaction within limits", corrId);
    }

    private Future<String> checkIdempotency(RiskRequest req) {
        if (req.idempotencyKey() == null || req.idempotencyKey().isBlank()) {
            return Future.succeededFuture(null);
        }
        return vertx.executeBlocking(() -> idempotencyStore.get(req.idempotencyKey()))
            .map(v -> (v == null || v.isBlank()) ? null : v);
    }

    private Future<Void> storeIdempotency(RiskRequest req, String decisionJson) {
        if (req.idempotencyKey() == null || req.idempotencyKey().isBlank()) {
            return Future.succeededFuture();
        }
        return vertx.executeBlocking(() -> {
            idempotencyStore.putIfAbsent(req.idempotencyKey(), decisionJson);
            return (Void) null;
        });
    }
}
