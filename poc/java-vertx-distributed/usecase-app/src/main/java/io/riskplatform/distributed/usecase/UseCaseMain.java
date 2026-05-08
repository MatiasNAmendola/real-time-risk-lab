package io.riskplatform.distributed.usecase;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.EventBusOptions;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;
import com.hazelcast.config.Config;
import com.hazelcast.config.XmlConfigBuilder;

import java.util.concurrent.TimeUnit;

public class UseCaseMain {

    public static void main(String[] args) {
        Config hzConfig = buildHazelcastConfig();
        HazelcastClusterManager mgr = new HazelcastClusterManager(hzConfig);

        // Pin EventBus advertised host to the docker-compose service name so
        // peers can resolve the reply address (default would be the container id).
        String ebHost = System.getenv().getOrDefault("EVENT_BUS_HOST", "usecase-app");
        VertxOptions vertxOptions = new VertxOptions()
            .setBlockedThreadCheckInterval(10)
            .setBlockedThreadCheckIntervalUnit(TimeUnit.SECONDS)
            .setMaxEventLoopExecuteTime(10)
            .setMaxEventLoopExecuteTimeUnit(TimeUnit.SECONDS)
            .setEventBusOptions(new EventBusOptions().setHost(ebHost).setClusterPublicHost(ebHost));

        Vertx.builder()
            .with(vertxOptions)
            .withClusterManager(mgr)
            .buildClustered()
            .onSuccess(vertx -> {
            vertx.deployVerticle(new EvaluateRiskVerticle()).onSuccess(id ->
                System.out.println("[usecase-app] EvaluateRiskVerticle deployed: " + id)
            ).onFailure(err -> {
                System.err.println("[usecase-app] Failed to deploy EvaluateRiskVerticle: " + err.getMessage());
                vertx.close();
            });
        }).onFailure(err -> {
            System.err.println("[usecase-app] Cluster join failed: " + err.getMessage());
            System.exit(1);
        });
    }

    static Config buildHazelcastConfig() {
        String configuredPath = System.getenv("HAZELCAST_CONFIG_PATH");
        String[] candidatePaths = configuredPath != null && !configuredPath.isBlank()
            ? new String[] { configuredPath }
            : new String[] {
                "/etc/riskplatform/hazelcast/hazelcast.xml",
                "poc/java-vertx-distributed/hazelcast/hazelcast.xml",
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
