package com.naranjax.distributed.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.naranjax.distributed.shared.EventBusAddress;
import com.naranjax.distributed.shared.FeatureSnapshot;
import com.naranjax.distributed.shared.RiskDecision;
import com.naranjax.distributed.shared.RiskRequest;
import com.naranjax.distributed.shared.resilience.LatencyBudget;
import com.naranjax.distributed.shared.resilience.SimpleCircuitBreaker;
import com.naranjax.distributed.shared.rules.FraudRule;
import com.naranjax.distributed.shared.rules.HighAmountRule;
import com.naranjax.distributed.shared.rules.NewDeviceYoungCustomerRule;
import com.naranjax.distributed.shared.rules.RuleEvaluation;
import com.naranjax.poc.risk.audit.RulesAuditTrail;
import com.naranjax.poc.risk.config.RulesConfigLoader;
import com.naranjax.poc.risk.engine.AggregateDecision;
import com.naranjax.poc.risk.engine.RuleEngine;
import com.naranjax.poc.risk.engine.RuleEngineImpl;
import com.naranjax.distributed.usecase.aws.S3AuditPublisher;
import com.naranjax.distributed.usecase.aws.SqsDecisionPublisher;
import io.opentelemetry.api.trace.Span;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Core usecase verticle: evaluates transaction risk end-to-end.
 *
 * <p>Handle flow:
 * <ol>
 *   <li>Parse {@link RiskRequest} from event-bus message.
 *   <li>Idempotency check – if {@code idempotencyKey} is present and already stored, return the
 *       cached decision immediately (no re-evaluation).
 *   <li>Start a {@link LatencyBudget} of 280 ms (leaving 20 ms of margin before the HTTP SLA).
 *   <li>Load {@link FeatureSnapshot} from repository-app (raw signals + derived score).
 *   <li>Evaluate named, versioned {@link FraudRule} objects (HighAmountRule, NewDeviceYoungCustomerRule).
 *   <li>Optionally call the ML scorer if the circuit breaker allows and the budget has room.
 *   <li>Combine rule results and ML score into a final decision.
 *   <li>Store the decision under the idempotency key.
 *   <li>Publish to Kafka, S3 audit, SQS, and broadcast for SSE/WS.
 * </ol>
 */
public class EvaluateRiskVerticle extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(EvaluateRiskVerticle.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long TIMEOUT_MS    = 10_000L;
    private static final long BUDGET_MS     = 280L;
    private static final String KAFKA_TOPIC = "risk-decisions";

    /** Named, versioned fraud rules (legacy — kept for fallback path). */
    private static final List<FraudRule> LEGACY_RULES = List.of(
        new HighAmountRule(),
        new NewDeviceYoungCustomerRule()
    );

    /** Declarative rule engine loaded from RULES_CONFIG_PATH. */
    private RuleEngine ruleEngine;
    private RulesAuditTrail rulesAuditTrail;

    /**
     * Circuit breaker protecting the optional ML scorer call.
     * Opens after 3 consecutive failures; resets after 30 seconds.
     */
    private final SimpleCircuitBreaker mlBreaker =
        new SimpleCircuitBreaker(3, 30_000L);

    private KafkaProducer<String, String> kafkaProducer;
    private S3AuditPublisher   s3AuditPublisher;
    private SqsDecisionPublisher sqsDecisionPublisher;

    @Override
    public void start(Promise<Void> startPromise) {
        // Bootstrap declarative rules engine
        rulesAuditTrail = new RulesAuditTrail();
        String rulesConfigPath = System.getenv().getOrDefault(
                "RULES_CONFIG_PATH", "examples/rules-config/v1/rules.yaml");
        try {
            var loader = new RulesConfigLoader();
            var config = loader.load(rulesConfigPath);
            ruleEngine = new RuleEngineImpl(config, rulesAuditTrail);
            log.info("[usecase-app] Rules engine loaded from {}, version={}, hash={}",
                    rulesConfigPath, config.version(), config.hash());
        } catch (Exception e) {
            log.warn("[usecase-app] Could not load rules config from {}, using legacy rules: {}",
                    rulesConfigPath, e.getMessage());
            ruleEngine = null;
        }

        Map<String, String> kafkaConfig = new HashMap<>();
        kafkaConfig.put("bootstrap.servers",
            System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "redpanda:9092"));
        kafkaConfig.put("key.serializer",   "org.apache.kafka.common.serialization.StringSerializer");
        kafkaConfig.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        kafkaConfig.put("acks", "1");
        kafkaConfig.put("client.id", "usecase-app-producer");

        kafkaProducer = KafkaProducer.create(vertx, kafkaConfig);

        // AWS SDK client construction performs network I/O (e.g. SQS getQueueUrl)
        // and STS/region resolution that can block the event loop for several
        // seconds — see BlockedThreadChecker warnings on cold-start. Off-load
        // this init to a worker thread so the event loop stays free during boot.
        // Only register the EventBus consumer after AWS init so handleEvaluate
        // never sees null publishers.
        final Map<String, String> kafkaConfigFinal = kafkaConfig;
        vertx.<Void>executeBlocking(() -> {
            long t0 = System.currentTimeMillis();
            s3AuditPublisher     = new S3AuditPublisher(vertx);
            sqsDecisionPublisher = new SqsDecisionPublisher(vertx);
            log.info("[usecase-app] AWS SDK init complete (off event loop) in {} ms",
                System.currentTimeMillis() - t0);
            return null;
        }, false)
            .compose(v -> vertx.eventBus()
                .<String>consumer(EventBusAddress.USECASE_EVALUATE, this::handleEvaluate)
                .completion())
            .onSuccess(v -> {
                log.info("[usecase-app] EvaluateRiskVerticle ready, Kafka -> {}",
                    kafkaConfigFinal.get("bootstrap.servers"));
                startPromise.complete();
            })
            .onFailure(err -> {
                log.error("[usecase-app] start failed: {}", err.getMessage(), err);
                startPromise.fail(err);
            });
    }

    // ── Main handler ──────────────────────────────────────────────────────────

    private void handleEvaluate(Message<String> msg) {
        String correlationId = msg.headers().get("correlationId");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        final String corrId = correlationId;

        MDC.put("correlationId", corrId);
        log.info("[usecase-app] evaluating risk body={}", msg.body());

        // -- 1. Parse request ---------------------------------------------------
        RiskRequest req;
        try {
            req = MAPPER.readValue(msg.body(), RiskRequest.class);
        } catch (Exception e) {
            MDC.clear();
            msg.fail(400, "invalid request: " + e.getMessage());
            return;
        }

        // Propagate corrId from the HTTP header into the request if not already set
        final RiskRequest finalReq = req.correlationId() == null
            ? new RiskRequest(req.transactionId(), req.customerId(), req.amountCents(),
                corrId, req.idempotencyKey(), req.newDevice())
            : req;

        // -- 2. Idempotency check -----------------------------------------------
        checkIdempotency(finalReq, corrId)
            .onSuccess(cached -> {
                if (cached != null) {
                    log.info("[usecase-app] idempotency hit key={}", finalReq.idempotencyKey());
                    Span.current().setAttribute("risk.idempotency_hit", true);
                    msg.reply(cached);
                    MDC.clear();
                    return;
                }
                // -- 3. Latency budget -----------------------------------------
                LatencyBudget budget = new LatencyBudget(BUDGET_MS);
                continueEvaluation(msg, finalReq, corrId, budget);
            })
            .onFailure(err -> {
                log.warn("[usecase-app] idempotency check failed, continuing: {}", err.getMessage());
                LatencyBudget budget = new LatencyBudget(BUDGET_MS);
                continueEvaluation(msg, finalReq, corrId, budget);
            });
    }

    private void continueEvaluation(Message<String> msg, RiskRequest req,
                                    String corrId, LatencyBudget budget) {
        DeliveryOptions opts = new DeliveryOptions()
            .setSendTimeout(TIMEOUT_MS)
            .addHeader("correlationId", corrId);

        // -- 4. Load FeatureSnapshot -------------------------------------------
        vertx.eventBus().<String>request(
            EventBusAddress.REPOSITORY_FIND_FEATURES,
            req.customerId(),
            opts
        ).onSuccess(reply -> {
            FeatureSnapshot snapshot;
            try {
                snapshot = MAPPER.readValue(reply.body(), FeatureSnapshot.class);
            } catch (Exception e) {
                MDC.clear();
                msg.fail(500, "invalid snapshot: " + e.getMessage());
                return;
            }

            // -- 5. Named rule evaluation (engine or legacy fallback) ----------
            List<RuleEvaluation> ruleResults;
            AggregateDecision engineDecision = null;
            if (ruleEngine != null) {
                com.naranjax.poc.risk.engine.FeatureSnapshot featureSnap =
                        com.naranjax.poc.risk.engine.FeatureSnapshot.builder()
                        .customerId(req.customerId())
                        .transactionId(req.transactionId())
                        .amountCents(req.amountCents())
                        .newDevice(req.newDevice())
                        .transactionCount10m(0)
                        .chargebackCount90d(0)
                        .build();
                engineDecision = ruleEngine.evaluate(featureSnap);
                // Adapt to legacy RuleEvaluation format for downstream metrics/spans
                ruleResults = engineDecision.ruleResults().stream()
                        .filter(com.naranjax.poc.risk.rule.RuleEvaluation::triggered)
                        .map(re -> new RuleEvaluation(true, re.reason(), re.weight()))
                        .toList();
            } else {
                ruleResults = LEGACY_RULES.stream()
                        .map(r -> r.evaluate(req, snapshot))
                        .toList();
            }

            // -- 6. Optional ML score (budget + circuit breaker guard) ---------
            double mlScore        = snapshot.riskScore(); // default: pre-materialised DB score
            boolean fallbackApplied = false;

            if (budget.canSpend(80L) && mlBreaker.allowRequest()) {
                try {
                    mlScore = invokeFakeMlScorer(req, snapshot);
                    mlBreaker.recordSuccess();
                } catch (Exception e) {
                    mlBreaker.recordFailure();
                    fallbackApplied = true;
                    log.warn("[usecase-app] ML scorer failed (breaker={}), using pre-materialised score: {}",
                        mlBreaker.currentState(), e.getMessage());
                }
            } else if (!mlBreaker.allowRequest()) {
                fallbackApplied = true;
                log.info("[usecase-app] circuit breaker OPEN, skipping ML scorer");
            } else {
                fallbackApplied = true;
                log.info("[usecase-app] latency budget exhausted before ML scorer, remaining={}ms",
                    budget.remainingMillis());
            }

            // -- 7. Final decision policy (mirrors bare-javac) -----------------
            RiskDecision decision = applyPolicy(ruleResults, mlScore, corrId,
                req.transactionId(), fallbackApplied);

            // -- 8. OTel span attributes ----------------------------------------
            Span span = Span.current();
            span.setAttribute("risk.decision",          decision.decision());
            span.setAttribute("risk.ml.score",          mlScore);
            span.setAttribute("risk.budget.remaining_ms", budget.remainingMillis());
            span.setAttribute("risk.fallback_applied",  fallbackApplied);
            span.setAttribute("risk.amount_cents",      req.amountCents());
            span.setAttribute("risk.correlation_id",    corrId);
            span.setAttribute("risk.cb.state",          mlBreaker.currentState().name());
            for (int i = 0; i < LEGACY_RULES.size() && i < ruleResults.size(); i++) {
                String ruleName = LEGACY_RULES.get(i).name();
                boolean triggered = ruleResults.get(i).triggered();
                span.setAttribute("risk.rule." + ruleName, triggered);
            }

            log.info("[usecase-app] decision={} amount={} mlScore={} fallback={} correlationId={}",
                decision.decision(), req.amountCents(), mlScore, fallbackApplied, corrId);

            // -- 9. Broadcast + async outputs ----------------------------------
            JsonObject broadcastPayload = new JsonObject()
                .put("decision",       decision.decision())
                .put("reason",         decision.reason())
                .put("correlationId",  corrId)
                .put("transactionId",  req.transactionId())
                .put("fallback",       fallbackApplied)
                .put("fallbackReason", fallbackApplied ? "ml-scorer-timeout" : null)
                .put("decisionSource", fallbackApplied ? "rules" : "ml");
            vertx.eventBus().publish(EventBusAddress.RISK_DECISION_BROADCAST, broadcastPayload.encode());

            publishToKafka(req, decision, corrId);

            s3AuditPublisher.publishAudit(req, decision, corrId)
                .onFailure(err -> log.error("[usecase-app] S3 audit failed: {}", err.getMessage()));

            sqsDecisionPublisher.publish(req, decision, corrId)
                .onFailure(err -> log.error("[usecase-app] SQS publish failed: {}", err.getMessage()));

            // -- 10. Store idempotency + reply ---------------------------------
            String decisionJson;
            try {
                decisionJson = MAPPER.writeValueAsString(decision);
            } catch (Exception e) {
                MDC.clear();
                msg.fail(500, "serialization error: " + e.getMessage());
                return;
            }

            storeIdempotency(req, decisionJson, corrId)
                .onComplete(ar -> {
                    // Idempotency store is best-effort; reply regardless of outcome
                    msg.reply(decisionJson);
                    MDC.clear();
                });

        }).onFailure(err -> {
            log.error("[usecase-app] repository error: {}", err.getMessage());
            MDC.clear();
            msg.fail(502, "repository unavailable: " + err.getMessage());
        });
    }

    // ── Policy ────────────────────────────────────────────────────────────────

    /**
     * Combines named rule results and ML score into a final decision.
     *
     * <p>Priority (highest wins):
     * <ol>
     *   <li>HighAmountRule fired → DECLINE
     *   <li>NewDeviceYoungCustomerRule fired → REVIEW
     *   <li>mlScore > 0.7 → DECLINE
     *   <li>mlScore > 0.4 → REVIEW
     *   <li>else → APPROVE
     * </ol>
     */
    private RiskDecision applyPolicy(List<RuleEvaluation> ruleResults, double mlScore,
                                     String correlationId, String transactionId,
                                     boolean fallback) {
        // Rule index 0 = HighAmountRule, index 1 = NewDeviceYoungCustomerRule
        boolean highAmountFired = ruleResults.size() > 0 && ruleResults.get(0).triggered();
        boolean newDeviceFired  = ruleResults.size() > 1 && ruleResults.get(1).triggered();

        if (highAmountFired) {
            return new RiskDecision("DECLINE",
                "HighAmountRule v1: " + ruleResults.get(0).reason(), correlationId);
        }
        if (newDeviceFired) {
            return new RiskDecision("REVIEW",
                "NewDeviceYoungCustomerRule v1: " + ruleResults.get(1).reason(), correlationId);
        }
        if (mlScore > 0.7) {
            return new RiskDecision("DECLINE",
                "ml score " + mlScore + " > 0.7", correlationId);
        }
        if (mlScore > 0.4) {
            return new RiskDecision("REVIEW",
                "ml score " + mlScore + " > 0.4", correlationId);
        }
        return new RiskDecision("APPROVE",
            "transaction within limits", correlationId);
    }

    // ── ML scorer (inline fake — no external call in PoC) ────────────────────

    /**
     * Simulates an ML scorer call.
     * In production this would be an HTTP/gRPC call to a model-serving endpoint.
     * The circuit breaker wraps this method.
     */
    private double invokeFakeMlScorer(RiskRequest req, FeatureSnapshot fs) {
        // Fake: derive a score from the pre-materialised riskScore with minor noise.
        // This keeps the PoC self-contained while demonstrating the scorer boundary.
        return fs.riskScore();
    }

    // ── Idempotency helpers ───────────────────────────────────────────────────

    private Future<String> checkIdempotency(RiskRequest req, String corrId) {
        if (req.idempotencyKey() == null || req.idempotencyKey().isBlank()) {
            return Future.succeededFuture(null);
        }
        DeliveryOptions opts = new DeliveryOptions().setSendTimeout(TIMEOUT_MS)
            .addHeader("correlationId", corrId);
        return vertx.eventBus().<String>request(
                EventBusAddress.REPOSITORY_IDEMPOTENCY_GET, req.idempotencyKey(), opts)
            .map(msg -> {
                String body = msg.body();
                return (body == null || body.isBlank()) ? null : body;
            });
    }

    private Future<Void> storeIdempotency(RiskRequest req, String decisionJson, String corrId) {
        if (req.idempotencyKey() == null || req.idempotencyKey().isBlank()) {
            return Future.succeededFuture();
        }
        String body = req.idempotencyKey() + "\n" + decisionJson;
        DeliveryOptions opts = new DeliveryOptions().setSendTimeout(TIMEOUT_MS)
            .addHeader("correlationId", corrId);
        return vertx.eventBus().<String>request(
                EventBusAddress.REPOSITORY_IDEMPOTENCY_PUT, body, opts)
            .mapEmpty();
    }

    // ── Kafka publish ─────────────────────────────────────────────────────────

    private void publishToKafka(RiskRequest req, RiskDecision decision, String correlationId) {
        String eventId = UUID.randomUUID().toString();
        JsonObject event = new JsonObject()
            .put("eventId",       eventId)
            .put("eventVersion",  1)
            .put("correlationId", correlationId)
            .put("occurredAt",    Instant.now().toString())
            .put("transactionId", req.transactionId())
            .put("decision",      decision.decision())
            .put("reason",        decision.reason());

        Span span = Span.current();
        String traceId    = span.getSpanContext().getTraceId();
        String spanId     = span.getSpanContext().getSpanId();
        String traceparent = "00-" + traceId + "-" + spanId + "-01";

        KafkaProducerRecord<String, String> record = KafkaProducerRecord.create(
            KAFKA_TOPIC, req.transactionId(), event.encode());
        record.addHeader("traceparent",    traceparent);
        record.addHeader("correlationId",  correlationId);
        record.addHeader("idempotencyKey", eventId);

        kafkaProducer.send(record)
            .onSuccess(meta -> log.info("[usecase-app] Kafka published topic={} partition={} offset={}",
                KAFKA_TOPIC, meta.getPartition(), meta.getOffset()))
            .onFailure(err -> log.error("[usecase-app] Kafka publish failed: {}", err.getMessage()));
    }
}
