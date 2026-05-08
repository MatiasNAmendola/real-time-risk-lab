package io.riskplatform.monolith;

import io.riskplatform.monolith.config.ApplicationFactory;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.micrometer.MicrometerMetricsOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Single-JVM entry point for the java-monolith PoC.
 *
 * <p>All layers (controller, usecase, repository) are deployed as verticles
 * within the same Vert.x instance. Communication happens via the LOCAL event bus
 * — no Hazelcast cluster, no network hops between layers.
 *
 * <p>Architectural contrast with java-vertx-distributed:
 * <ul>
 *   <li>Distributed: 4 JVMs, Hazelcast cluster, event bus crosses network.
 *   <li>Monolith: 1 JVM, local event bus, in-process calls only.
 * </ul>
 *
 * <p>Both PoCs share the same external infrastructure (Postgres, Valkey,
 * Redpanda, MinIO, ElasticMQ, Moto, OpenBao, OpenObserve) and the same
 * pkg:risk-domain business rules.
 */
public class Application {

    private static final Logger log = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) {
        VertxOptions options = new VertxOptions()
            .setMetricsOptions(new MicrometerMetricsOptions().setEnabled(true));

        Vertx vertx = Vertx.vertx(options);

        ApplicationFactory factory = new ApplicationFactory(vertx);
        factory.deploy()
            .onSuccess(v -> log.info("[java-monolith] All verticles deployed — ready on port 8090"))
            .onFailure(err -> {
                log.error("[java-monolith] Startup failed: {}", err.getMessage(), err);
                vertx.close();
                System.exit(1);
            });
    }
}
