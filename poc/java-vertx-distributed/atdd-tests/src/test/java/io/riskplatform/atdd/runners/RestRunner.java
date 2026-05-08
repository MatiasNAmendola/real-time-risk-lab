package io.riskplatform.atdd.runners;

import com.intuit.karate.junit5.Karate;

/**
 * Runs only the REST-related features.
 * Useful during development to run a focused subset:
 * <pre>
 *   ./gradlew :poc:java-vertx-distributed:atdd-tests:test -Patdd --tests io.riskplatform.atdd.runners.RestRunner
 * </pre>
 */
class RestRunner {

    @Karate.Test
    Karate restFeatures() {
        return Karate.run(
                "classpath:features/01_health.feature",
                "classpath:features/02_rest_decision.feature",
                "classpath:features/07_idempotency.feature",
                "classpath:features/08_fallback_ml.feature",
                "classpath:features/09_decline_threshold.feature"
        );
    }
}
