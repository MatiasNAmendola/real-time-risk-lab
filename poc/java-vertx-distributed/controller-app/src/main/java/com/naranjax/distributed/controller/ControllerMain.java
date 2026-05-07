package com.naranjax.distributed.controller;

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.config.TcpIpConfig;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.EventBusOptions;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.micrometer.VertxPrometheusOptions;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

import java.util.Arrays;
import java.util.List;

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
        Config cfg = new Config();
        cfg.setClusterName("vertx-distributed-cluster");

        NetworkConfig network = cfg.getNetworkConfig();
        network.setPort(5701).setPortAutoIncrement(true);

        JoinConfig join = network.getJoin();
        join.getMulticastConfig().setEnabled(false);

        TcpIpConfig tcpIp = join.getTcpIpConfig().setEnabled(true);
        String members = System.getenv().getOrDefault(
            "HAZELCAST_MEMBERS", "controller-app:5701,usecase-app:5701,repository-app:5701"
        );
        List<String> memberList = Arrays.asList(members.split(","));
        tcpIp.setMembers(memberList);

        return cfg;
    }
}
