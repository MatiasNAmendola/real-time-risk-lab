package com.naranjax.atdd.runners;

import com.intuit.karate.junit5.Karate;

/**
 * Runs only the webhook callback feature.
 * <pre>
 *   mvn -pl atdd-tests test -Dtest=WebhookRunner
 * </pre>
 */
class WebhookRunner {

    @Karate.Test
    Karate webhookFeatures() {
        return Karate.run("classpath:features/05_webhook_callback.feature");
    }
}
