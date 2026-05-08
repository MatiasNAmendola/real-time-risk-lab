package io.riskplatform.monolith.atdd;

import com.intuit.karate.junit5.Karate;

/**
 * ATDD suite entry point for the java-monolith PoC.
 *
 * <p>Runs all feature files under classpath:features/. The karate-config.js
 * sets baseUrl to http://localhost:8090 (port 8090, distinct from the
 * java-vertx-distributed controller-app on port 8080).
 *
 * <p>Prerequisites: compose stack must be running with java-monolith on port 8090.
 */
class MonolithAtddSuite {

    @Karate.Test
    Karate runAll() {
        return Karate.run("classpath:features").relativeTo(getClass());
    }
}
