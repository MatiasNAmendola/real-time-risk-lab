package io.riskplatform.atdd.runners;

import com.intuit.karate.junit5.Karate;

/**
 * Runs only the Kafka-related feature.
 * <pre>
 *   ./gradlew :poc:java-vertx-distributed:atdd-tests:test -Patdd --tests io.riskplatform.atdd.runners.KafkaRunner
 * </pre>
 */
class KafkaRunner {

    @Karate.Test
    Karate kafkaFeatures() {
        return Karate.run("classpath:features/06_kafka_publish.feature");
    }
}
