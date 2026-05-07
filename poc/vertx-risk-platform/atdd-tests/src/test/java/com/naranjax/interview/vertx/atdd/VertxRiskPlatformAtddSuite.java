package com.naranjax.interview.vertx.atdd;

import com.intuit.karate.junit5.Karate;

/**
 * ATDD suite for the vertx-risk-platform PoC.
 *
 * Requires the 3 pods to be running:
 *   controller-pod  :8180
 *   usecase-pod     :8181
 *   repository-pod  :8182
 *
 * Run with:
 *   ./gradlew :poc:vertx-risk-platform:atdd-tests:test -Patdd
 * or via docker compose:
 *   ./nx up vertx-platform
 *   ./nx test --group atdd-vertx-platform
 */
class VertxRiskPlatformAtddSuite {

    @Karate.Test
    Karate runAll() {
        return Karate.run("classpath:features").relativeTo(getClass());
    }
}
