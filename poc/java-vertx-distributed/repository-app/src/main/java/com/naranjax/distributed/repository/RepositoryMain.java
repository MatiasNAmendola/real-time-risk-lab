package com.naranjax.distributed.repository;

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

public class RepositoryMain {

    public static void main(String[] args) {
        Config hzConfig = buildHazelcastConfig();
        HazelcastClusterManager mgr = new HazelcastClusterManager(hzConfig);

        String ebHost = System.getenv().getOrDefault("EVENT_BUS_HOST", "repository-app");
        VertxOptions vertxOptions = new VertxOptions()
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
