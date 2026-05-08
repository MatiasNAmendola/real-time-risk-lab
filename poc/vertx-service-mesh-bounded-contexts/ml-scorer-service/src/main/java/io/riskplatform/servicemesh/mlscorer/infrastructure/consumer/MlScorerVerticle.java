package io.riskplatform.servicemesh.mlscorer.infrastructure.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.riskplatform.servicemesh.mlscorer.domain.service.MlScoringService;
import io.riskplatform.servicemesh.shared.EventBusAddresses;
import io.riskplatform.servicemesh.shared.RiskRequest;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public final class MlScorerVerticle extends AbstractVerticle {
    private static final Logger log = LoggerFactory.getLogger(MlScorerVerticle.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private final MlScoringService scoringService = new MlScoringService();
    private final long artificialDelayMs = Long.parseLong(System.getenv().getOrDefault("ML_DELAY_MS", "12"));
    private final boolean failMode = Boolean.parseBoolean(System.getenv().getOrDefault("ML_FAIL", "false"));

    @Override
    public void start(Promise<Void> startPromise) {
        vertx.eventBus().<String>consumer(EventBusAddresses.ML_SCORER_SCORE, this::handle)
            .completion()
            .onSuccess(v -> {
                log.info("ml-scorer-service ready address={} delayMs={} failMode={}",
                    EventBusAddresses.ML_SCORER_SCORE, artificialDelayMs, failMode);
                startPromise.complete();
            })
            .onFailure(startPromise::fail);
    }

    private void handle(Message<String> msg) {
        String correlationId = msg.headers().get("correlationId");
        MDC.put("correlationId", correlationId);
        vertx.setTimer(artificialDelayMs, timer -> {
            try {
                if (failMode) {
                    throw new IllegalStateException("forced ML failure");
                }
                RiskRequest request = mapper.readValue(msg.body(), RiskRequest.class);
                var result = scoringService.score(request);
                log.info("ml scored transactionId={} score={}", request.transactionId(), result.score());
                msg.reply(mapper.writeValueAsString(result));
            } catch (Exception e) {
                log.error("ml scorer failed correlationId={}", correlationId, e);
                msg.fail(503, "ml-scorer failed: " + e.getMessage());
            } finally {
                MDC.clear();
            }
        });
    }
}
