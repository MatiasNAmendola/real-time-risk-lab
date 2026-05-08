package io.riskplatform.distributed.repository;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.EventBusOptions;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;
import com.hazelcast.config.Config;
import com.hazelcast.config.XmlConfigBuilder;

import java.util.concurrent.TimeUnit;

public class RepositoryMain {

    public static void main(String[] args) {
        Config hzConfig = buildHazelcastConfig();
        HazelcastClusterManager mgr = new HazelcastClusterManager(hzConfig);

        String ebHost = System.getenv().getOrDefault("EVENT_BUS_HOST", "repository-app");
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
            // SecretsBootstrap.resolveDbPassword() (called inside DbBootstrap.init)
            // performs a synchronous AWS Secrets Manager / OpenBao HTTP call and
            // builds AWS SDK clients. That blocks the event loop on cold-start
            // (BlockedThreadChecker fires). Run the bootstrap on a worker thread.
            vertx.<io.vertx.sqlclient.Pool>executeBlocking(() -> {
                long t0 = System.currentTimeMillis();
                io.vertx.sqlclient.Pool pool = DbBootstrap.init(vertx).toCompletionStage().toCompletableFuture().get();
                System.out.println("[repository-app] DB+secrets init complete (off event loop) in "
                    + (System.currentTimeMillis() - t0) + " ms");
                return pool;
            }, false).onSuccess(pool -> {
                vertx.deployVerticle(new FeatureRepositoryVerticle(pool))
                    .compose(id -> {
                        System.out.println("[repository-app] FeatureRepositoryVerticle deployed: " + id);
                        return vertx.deployVerticle(new IdempotencyVerticle());
                    })
                    .onSuccess(id ->
                        System.out.println("[repository-app] IdempotencyVerticle deployed: " + id)
                    ).onFailure(err -> {
                        System.err.println("[repository-app] deploy failed: " + err.getMessage());
                        vertx.close();
                    });
            }).onFailure(err -> {
                System.err.println("[repository-app] DB bootstrap failed: " + err.getMessage());
                vertx.close();
            });
        }).onFailure(err -> {
            System.err.println("[repository-app] Cluster join failed: " + err.getMessage());
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
