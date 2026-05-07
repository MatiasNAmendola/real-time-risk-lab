package com.naranjax.distributed.usecase;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.EventBusOptions;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;
import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.config.TcpIpConfig;

import java.util.Arrays;
import java.util.List;

public class UseCaseMain {

    public static void main(String[] args) {
        Config hzConfig = buildHazelcastConfig();
        HazelcastClusterManager mgr = new HazelcastClusterManager(hzConfig);

        // Pin EventBus advertised host to the docker-compose service name so
        // peers can resolve the reply address (default would be the container id).
        String ebHost = System.getenv().getOrDefault("EVENT_BUS_HOST", "usecase-app");
        VertxOptions vertxOptions = new VertxOptions()
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
