package io.riskplatform.servicemesh.audit.infrastructure.consumer;

import io.riskplatform.servicemesh.shared.EventBusAddresses;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public final class AuditVerticle extends AbstractVerticle {
    private static final Logger log = LoggerFactory.getLogger(AuditVerticle.class);

    @Override
    public void start(Promise<Void> startPromise) {
        vertx.eventBus().<String>consumer(EventBusAddresses.AUDIT_RECORD, this::handle)
            .completion()
            .onSuccess(v -> {
                log.info("audit-service ready address={}", EventBusAddresses.AUDIT_RECORD);
                startPromise.complete();
            })
            .onFailure(startPromise::fail);
    }

    private void handle(Message<String> msg) {
        String correlationId = msg.headers().get("correlationId");
        MDC.put("correlationId", correlationId);
        try {
            log.info("audit event recorded payload={}", msg.body());
            msg.reply("ACK");
        } finally {
            MDC.clear();
        }
    }
}
