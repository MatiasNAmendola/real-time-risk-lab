package com.naranjax.atdd.runners;

import com.intuit.karate.junit5.Karate;

/**
 * Runs only the Kafka-related feature.
 * <pre>
 *   mvn -pl atdd-tests test -Dtest=KafkaRunner
 * </pre>
 */
class KafkaRunner {

    @Karate.Test
    Karate kafkaFeatures() {
        return Karate.run("classpath:features/06_kafka_publish.feature");
    }
}
