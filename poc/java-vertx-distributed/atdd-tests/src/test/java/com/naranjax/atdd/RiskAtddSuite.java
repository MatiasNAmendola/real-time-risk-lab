package com.naranjax.atdd;

import com.intuit.karate.junit5.Karate;

/**
 * Main entry point for the ATDD suite.
 *
 * <p>Runs all feature files located under {@code classpath:features/}.
 * Execute with:
 * <pre>
 *   mvn -pl atdd-tests test
 * </pre>
 * or with a specific Karate environment:
 * <pre>
 *   mvn -pl atdd-tests test -Dkarate.env=ci
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
