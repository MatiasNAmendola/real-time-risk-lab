package io.riskplatform.vertx.atdd;

import com.intuit.karate.junit5.Karate;

/**
 * ATDD suite for the vertx-layer-as-pod-http PoC.
 *
 * Requires the 3 pods to be running:
 *   controller-pod  :8180
 *   usecase-pod     :8181
 *   repository-pod  :8182
 *
 * Run with:
 *   ./gradlew :poc:vertx-layer-as-pod-http:atdd-tests:test -Patdd
 * or via docker compose:
 *   ./nx up vertx-platform
 *   ./nx test --group atdd-vertx-layer-as-pod-http
 */
class VertxRiskPlatformAtddSuite {

    @Karate.Test
    Karate runAll() {
        return Karate.run("classpath:features").relativeTo(getClass());
    }
}
