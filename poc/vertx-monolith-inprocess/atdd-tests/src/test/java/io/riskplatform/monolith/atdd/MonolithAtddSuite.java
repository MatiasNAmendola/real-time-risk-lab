package io.riskplatform.monolith.atdd;

import com.intuit.karate.junit5.Karate;

/**
 * ATDD suite entry point for the vertx-monolith-inprocess PoC.
 *
 * <p>Runs all feature files under classpath:features/. The karate-config.js
 * sets baseUrl to http://localhost:8090 (port 8090, distinct from the
 * vertx-layer-as-pod-eventbus controller-app on port 8080).
 *
 * <p>Prerequisites: compose stack must be running with vertx-monolith-inprocess on port 8090.
 */
class MonolithAtddSuite {

    @Karate.Test
    Karate runAll() {
        return Karate.run("classpath:features").relativeTo(getClass());
    }
}
