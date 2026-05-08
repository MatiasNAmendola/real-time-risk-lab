package io.riskplatform.atdd.runners;

import com.intuit.karate.junit5.Karate;

/**
 * Runs only the webhook callback feature.
 * <pre>
 *   ./gradlew :poc:vertx-layer-as-pod-eventbus:atdd-tests:test -Patdd --tests io.riskplatform.atdd.runners.WebhookRunner
 * </pre>
 */
class WebhookRunner {

    @Karate.Test
    Karate webhookFeatures() {
        return Karate.run("classpath:features/05_webhook_callback.feature");
    }
}
