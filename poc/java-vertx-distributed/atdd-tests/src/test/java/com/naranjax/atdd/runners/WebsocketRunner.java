package com.naranjax.atdd.runners;

import com.intuit.karate.junit5.Karate;

/**
 * Runs only the WebSocket bidirectional feature.
 * <pre>
 *   mvn -pl atdd-tests test -Dtest=WebsocketRunner
 * </pre>
 */
class WebsocketRunner {

    @Karate.Test
    Karate wsFeatures() {
        return Karate.run("classpath:features/04_websocket_bidi.feature");
    }
}
