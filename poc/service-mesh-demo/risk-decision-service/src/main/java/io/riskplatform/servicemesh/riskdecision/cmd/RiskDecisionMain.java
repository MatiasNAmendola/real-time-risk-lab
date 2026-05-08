package io.riskplatform.servicemesh.riskdecision.cmd;

import io.riskplatform.servicemesh.riskdecision.infrastructure.controller.RiskDecisionHttpVerticle;
import io.riskplatform.servicemesh.shared.ClusterBootstrap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RiskDecisionMain {
    private static final Logger log = LoggerFactory.getLogger(RiskDecisionMain.class);
    public static void main(String[] args) {
        ClusterBootstrap.clusteredVertx("risk-decision-service")
            .onSuccess(vertx -> vertx.deployVerticle(new RiskDecisionHttpVerticle())
                .onFailure(err -> { log.error("risk-decision-service deploy failed", err); vertx.close(); }))
            .onFailure(err -> { log.error("risk-decision-service cluster join failed", err); System.exit(1); });
    }
}
