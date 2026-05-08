package io.riskplatform.distributed.consumer;

import io.vertx.core.Vertx;

public class ConsumerMain {

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();  // standalone, no cluster needed
        vertx.deployVerticle(new RiskDecisionConsumerVerticle())
            .onSuccess(id -> System.out.println("[consumer-app] RiskDecisionConsumerVerticle deployed: " + id))
            .onFailure(err -> {
                System.err.println("[consumer-app] Deploy failed: " + err.getMessage());
                System.exit(1);
            });
    }
}
