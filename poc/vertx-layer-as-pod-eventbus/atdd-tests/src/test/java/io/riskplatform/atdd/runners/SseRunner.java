package io.riskplatform.atdd.runners;

import com.intuit.karate.junit5.Karate;

/**
 * Runs only the SSE (Server-Sent Events) feature.
 * Useful during development:
 * <pre>
 *   ./gradlew :poc:vertx-layer-as-pod-eventbus:atdd-tests:test -Patdd --tests io.riskplatform.atdd.runners.SseRunner
 * </pre>
 */
class SseRunner {

    @Karate.Test
    Karate sseFeatures() {
        return Karate.run("classpath:features/03_sse_stream.feature");
    }
}
