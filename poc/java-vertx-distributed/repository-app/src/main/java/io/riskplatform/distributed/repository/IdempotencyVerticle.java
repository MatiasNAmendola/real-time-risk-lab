package io.riskplatform.distributed.repository;

import io.riskplatform.distributed.shared.EventBusAddress;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;

import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory idempotency store for risk decisions.
 *
 * <p>Listens on two event-bus addresses:
 * <ul>
 *   <li>{@link EventBusAddress#REPOSITORY_IDEMPOTENCY_GET} – body is the idempotency key (String).
 *       Replies with the stored decision JSON, or an empty string if not found.
 *   <li>{@link EventBusAddress#REPOSITORY_IDEMPOTENCY_PUT} – body is a JSON object with fields
 *       {@code key} (String) and {@code decision} (String, the serialised RiskDecision JSON).
 *       Uses put-if-absent semantics. Replies with {@code "ok"}.
 * </ul>
 *
 * <p>This implementation intentionally uses a {@link ConcurrentHashMap} so it is correct for
 * the PoC without an external dependency.  In the AWS wire phase, this verticle is replaced by
 * {@code ValkeyIdempotencyStore} backed by a Redis/Valkey SET NX EX command.
 */
public class IdempotencyVerticle extends AbstractVerticle {

    private final ConcurrentHashMap<String, String> store = new ConcurrentHashMap<>();

    @Override
    public void start(Promise<Void> startPromise) {
        var eb = vertx.eventBus();

        eb.<String>consumer(EventBusAddress.REPOSITORY_IDEMPOTENCY_GET, this::handleGet)
            .completion()
            .compose(v -> eb.<String>consumer(EventBusAddress.REPOSITORY_IDEMPOTENCY_PUT, this::handlePut)
                .completion())
            .compose(v -> eb.<String>consumer(EventBusAddress.REPOSITORY_READY, msg -> msg.reply("READY"))
                .completion())
            .onSuccess(v -> {
                System.out.println("[repository-app] IdempotencyVerticle ready (in-memory store)");
                startPromise.complete();
            })
            .onFailure(startPromise::fail);
    }

    private void handleGet(Message<String> msg) {
        String key     = msg.body();
        String cached  = store.get(key);
        msg.reply(cached != null ? cached : "");
    }

    private void handlePut(Message<String> msg) {
        // Body format: key\n<decision-json>
        // Using a simple delimiter avoids a JSON parse dependency here.
        String body = msg.body();
        int sep = body.indexOf('\n');
        if (sep < 0) {
            msg.fail(400, "malformed idempotency put body");
            return;
        }
        String key      = body.substring(0, sep);
        String decision = body.substring(sep + 1);
        store.putIfAbsent(key, decision);
        msg.reply("ok");
    }
}
