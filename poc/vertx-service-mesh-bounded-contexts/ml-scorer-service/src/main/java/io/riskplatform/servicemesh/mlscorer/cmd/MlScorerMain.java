package io.riskplatform.servicemesh.mlscorer.cmd;

import io.riskplatform.servicemesh.mlscorer.infrastructure.consumer.MlScorerVerticle;
import io.riskplatform.servicemesh.shared.ClusterBootstrap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MlScorerMain {
    private static final Logger log = LoggerFactory.getLogger(MlScorerMain.class);
    public static void main(String[] args) {
        ClusterBootstrap.clusteredVertx("ml-scorer-service")
            .onSuccess(vertx -> vertx.deployVerticle(new MlScorerVerticle())
                .onFailure(err -> { log.error("ml-scorer-service deploy failed", err); vertx.close(); }))
            .onFailure(err -> { log.error("ml-scorer-service cluster join failed", err); System.exit(1); });
    }
}
