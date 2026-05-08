package io.riskplatform.servicemesh.fraudrules.infrastructure.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.riskplatform.servicemesh.fraudrules.domain.rule.RulePolicy;
import io.riskplatform.servicemesh.shared.EventBusAddresses;
import io.riskplatform.servicemesh.shared.RiskRequest;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public final class FraudRulesVerticle extends AbstractVerticle {
    private static final Logger log = LoggerFactory.getLogger(FraudRulesVerticle.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private final RulePolicy policy = new RulePolicy();

    @Override
    public void start(Promise<Void> startPromise) {
        vertx.eventBus().<String>consumer(EventBusAddresses.FRAUD_RULES_EVALUATE, this::handle)
            .completion()
            .onSuccess(v -> {
                log.info("fraud-rules-service ready address={}", EventBusAddresses.FRAUD_RULES_EVALUATE);
                startPromise.complete();
            })
            .onFailure(startPromise::fail);
    }

    private void handle(Message<String> msg) {
        String correlationId = msg.headers().get("correlationId");
        MDC.put("correlationId", correlationId);
        try {
            RiskRequest request = mapper.readValue(msg.body(), RiskRequest.class);
            var result = policy.evaluate(request);
            log.info("rules evaluated transactionId={} recommendation={} fired={}",
                request.transactionId(), result.recommendation(), result.firedRules());
            msg.reply(mapper.writeValueAsString(result));
        } catch (Exception e) {
            log.error("fraud rules evaluation failed correlationId={}", correlationId, e);
            msg.fail(422, "fraud-rules failed: " + e.getMessage());
        } finally {
            MDC.clear();
        }
    }
}
