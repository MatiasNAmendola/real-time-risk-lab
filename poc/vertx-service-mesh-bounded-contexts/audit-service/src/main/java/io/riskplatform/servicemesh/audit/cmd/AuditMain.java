package io.riskplatform.servicemesh.audit.cmd;

import io.riskplatform.servicemesh.audit.infrastructure.consumer.AuditVerticle;
import io.riskplatform.servicemesh.shared.ClusterBootstrap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AuditMain {
    private static final Logger log = LoggerFactory.getLogger(AuditMain.class);
    public static void main(String[] args) {
        ClusterBootstrap.clusteredVertx("audit-service")
            .onSuccess(vertx -> vertx.deployVerticle(new AuditVerticle())
                .onFailure(err -> { log.error("audit-service deploy failed", err); vertx.close(); }))
            .onFailure(err -> { log.error("audit-service cluster join failed", err); System.exit(1); });
    }
}
