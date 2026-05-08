package io.riskplatform.atdd.runners;

import com.intuit.karate.junit5.Karate;

/**
 * Runs only the WebSocket bidirectional feature.
 * <pre>
 *   ./gradlew :poc:vertx-layer-as-pod-eventbus:atdd-tests:test -Patdd --tests io.riskplatform.atdd.runners.WebsocketRunner
 * </pre>
 */
class WebsocketRunner {

    @Karate.Test
    Karate wsFeatures() {
        return Karate.run("classpath:features/04_websocket_bidi.feature");
    }
}
