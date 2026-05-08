package io.riskplatform.servicemesh.fraudrules.cmd;

import io.riskplatform.servicemesh.fraudrules.infrastructure.consumer.FraudRulesVerticle;
import io.riskplatform.servicemesh.shared.ClusterBootstrap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FraudRulesMain {
    private static final Logger log = LoggerFactory.getLogger(FraudRulesMain.class);
    public static void main(String[] args) {
        ClusterBootstrap.clusteredVertx("fraud-rules-service")
            .onSuccess(vertx -> vertx.deployVerticle(new FraudRulesVerticle())
                .onFailure(err -> { log.error("fraud-rules-service deploy failed", err); vertx.close(); }))
            .onFailure(err -> { log.error("fraud-rules-service cluster join failed", err); System.exit(1); });
    }
}
