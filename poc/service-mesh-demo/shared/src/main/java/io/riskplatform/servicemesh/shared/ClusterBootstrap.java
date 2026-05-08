package io.riskplatform.servicemesh.shared;

import com.hazelcast.config.Config;
import com.hazelcast.config.XmlConfigBuilder;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.EventBusOptions;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

public final class ClusterBootstrap {
    private ClusterBootstrap() {}

    public static Future<Vertx> clusteredVertx(String defaultEventBusHost) {
        String ebHost = System.getenv().getOrDefault("EVENT_BUS_HOST", defaultEventBusHost);
        Config hzConfig;
        try {
            hzConfig = new XmlConfigBuilder(System.getenv().getOrDefault(
                "HAZELCAST_CONFIG_PATH", "poc/service-mesh-demo/hazelcast/hazelcast.xml")).build();
        } catch (Exception e) {
            return Future.failedFuture(new IllegalStateException("failed to load Hazelcast config", e));
        }
        VertxOptions options = new VertxOptions()
            .setEventBusOptions(new EventBusOptions().setHost(ebHost).setClusterPublicHost(ebHost))
            .setMetricsOptions(new MicrometerMetricsOptions().setEnabled(true));
        return Vertx.builder()
            .with(options)
            .withClusterManager(new HazelcastClusterManager(hzConfig))
            .buildClustered();
    }
}
