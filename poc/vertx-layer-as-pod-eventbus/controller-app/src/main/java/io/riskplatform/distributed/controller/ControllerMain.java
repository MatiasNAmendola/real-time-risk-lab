package io.riskplatform.distributed.controller;

import com.hazelcast.config.Config;
import com.hazelcast.config.XmlConfigBuilder;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.EventBusOptions;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.micrometer.VertxPrometheusOptions;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

import java.util.concurrent.TimeUnit;

public class ControllerMain {

    public static void main(String[] args) {
        Config hzConfig = buildHazelcastConfig();
        HazelcastClusterManager mgr = new HazelcastClusterManager(hzConfig);

        // Enable Micrometer metrics (OTel agent will pick up via OTLP exporter)
        // Set EventBus host to the docker-compose service name so other cluster
        // members can resolve the reply address back to this container.
        // Without this, Vertx advertises InetAddress.getLocalHost().getHostName()
        // which is the random container id (NXDOMAIN from peers).
        String ebHost = System.getenv().getOrDefault("EVENT_BUS_HOST", "controller-app");
        VertxOptions vertxOptions = new VertxOptions()
            .setBlockedThreadCheckInterval(10)
            .setBlockedThreadCheckIntervalUnit(TimeUnit.SECONDS)
            .setMaxEventLoopExecuteTime(10)
            .setMaxEventLoopExecuteTimeUnit(TimeUnit.SECONDS)
            .setEventBusOptions(new EventBusOptions().setHost(ebHost).setClusterPublicHost(ebHost))
            .setMetricsOptions(new MicrometerMetricsOptions()
                .setEnabled(true));

        Vertx.builder()
            .with(vertxOptions)
            .withClusterManager(mgr)
            .buildClustered()
            .onSuccess(vertx -> {
                vertx.deployVerticle(new HttpVerticle()).onSuccess(id ->
                    System.out.println("[controller-app] HttpVerticle deployed: " + id)
                ).onFailure(err -> {
                    System.err.println("[controller-app] Failed to deploy HttpVerticle: " + err.getMessage());
                    vertx.close();
                });
            }).onFailure(err -> {
                System.err.println("[controller-app] Cluster join failed: " + err.getMessage());
                System.exit(1);
            });
    }

    static Config buildHazelcastConfig() {
        String configuredPath = System.getenv("HAZELCAST_CONFIG_PATH");
        String[] candidatePaths = configuredPath != null && !configuredPath.isBlank()
            ? new String[] { configuredPath }
            : new String[] {
                "/etc/riskplatform/hazelcast/hazelcast.xml",
                "poc/vertx-layer-as-pod-eventbus/hazelcast/hazelcast.xml",
                "hazelcast/hazelcast.xml",
                "../hazelcast/hazelcast.xml"
            };

        Exception lastError = null;
        for (String configPath : candidatePaths) {
            try {
                return new XmlConfigBuilder(configPath).build();
            } catch (Exception e) {
                lastError = e;
            }
        }
        throw new IllegalStateException("Failed to load Hazelcast config from any candidate path", lastError);
    }
}
