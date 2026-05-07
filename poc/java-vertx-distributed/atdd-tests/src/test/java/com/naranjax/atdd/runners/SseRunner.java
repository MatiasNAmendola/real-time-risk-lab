package com.naranjax.atdd.runners;

import com.intuit.karate.junit5.Karate;

/**
 * Runs only the SSE (Server-Sent Events) feature.
 * Useful during development:
 * <pre>
 *   mvn -pl atdd-tests test -Dtest=SseRunner
 * </pre>
 */
class SseRunner {

    @Karate.Test
    Karate sseFeatures() {
        return Karate.run("classpath:features/03_sse_stream.feature");
    }
}
