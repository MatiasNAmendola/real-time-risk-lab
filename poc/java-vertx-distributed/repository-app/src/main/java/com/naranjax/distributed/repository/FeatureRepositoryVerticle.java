package com.naranjax.distributed.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.naranjax.distributed.shared.EventBusAddress;
import com.naranjax.distributed.shared.FeatureSnapshot;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Tuple;

public class FeatureRepositoryVerticle extends AbstractVerticle {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final Pool pool;

    public FeatureRepositoryVerticle(Pool pool) {
        this.pool = pool;
    }

    @Override
    public void start(Promise<Void> startPromise) {
        vertx.eventBus().<String>consumer(EventBusAddress.REPOSITORY_FIND_FEATURES, this::handleFindFeatures)
            .completion()
            .onSuccess(v -> startPromise.complete())
            .onFailure(startPromise::fail);
    }

    private void handleFindFeatures(Message<String> msg) {
        String customerId = msg.body();
        System.out.println("[repository-app] loaded features for " + customerId);

        pool.preparedQuery(
                "SELECT customer_id, risk_score, country, customer_age_days, chargeback_count_90d, known_device" +
                " FROM customer_features WHERE customer_id = $1"
            )
            .execute(Tuple.of(customerId))
            .onSuccess(rows -> {
                if (rows.rowCount() == 0) {
                    // Unknown customer → default safe snapshot (conservative defaults)
                    FeatureSnapshot defaultSnapshot = new FeatureSnapshot(
                        customerId, 0.5, "UNKNOWN", 365, 0, true);
                    replyWithSnapshot(msg, defaultSnapshot);
                    return;
                }
                var row = rows.iterator().next();
                FeatureSnapshot snapshot = new FeatureSnapshot(
                    row.getString("customer_id"),
                    row.getDouble("risk_score"),
                    row.getString("country"),
                    row.getInteger("customer_age_days"),
                    row.getInteger("chargeback_count_90d"),
                    row.getBoolean("known_device")
                );
                replyWithSnapshot(msg, snapshot);
            })
            .onFailure(err -> {
                System.err.println("[repository-app] DB query failed: " + err.getMessage());
                msg.fail(500, "DB error: " + err.getMessage());
            });
    }

    private void replyWithSnapshot(Message<String> msg, FeatureSnapshot snapshot) {
        try {
            msg.reply(MAPPER.writeValueAsString(snapshot));
        } catch (Exception e) {
            msg.fail(500, "serialization error: " + e.getMessage());
        }
    }
}
