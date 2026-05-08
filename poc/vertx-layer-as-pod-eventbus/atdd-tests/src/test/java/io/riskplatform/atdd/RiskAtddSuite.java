package io.riskplatform.atdd;

import com.intuit.karate.junit5.Karate;

/**
 * Main entry point for the ATDD suite.
 *
 * <p>Runs all feature files located under {@code classpath:features/}.
 * Execute with:
 * <pre>
 *   ./gradlew :poc:vertx-layer-as-pod-eventbus:atdd-tests:test -Patdd
 * </pre>
 * or with a specific Karate environment:
 * <pre>
 *   ./gradlew :poc:vertx-layer-as-pod-eventbus:atdd-tests:test -Patdd -Dkarate.env=ci
 * </pre>
 *
 * <p>Prerequisites: {@code docker compose up} must be running before invoking.
 */
class RiskAtddSuite {

    @Karate.Test
    Karate runAll() {
        return Karate.run("classpath:features").relativeTo(getClass());
    }
}
