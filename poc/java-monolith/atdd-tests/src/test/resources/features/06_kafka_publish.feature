@kafka
Feature: Kafka event publication after risk decision

  # After every POST /risk, the usecase-app (or controller-app via outbox)
  # publishes a RiskDecisionEvent to the risk-decisions topic.
  # This feature verifies the event schema using an ephemeral consumer.

  Background:
    * url baseUrl
    * def KafkaSteps = Java.type('com.naranjax.atdd.support.KafkaSteps')

  Scenario: POST /risk publishes a well-formed event to risk-decisions topic
    # Use a unique transactionId to avoid matching events from other scenarios
    * def txId = 'kafka-tx-' + java.util.UUID.randomUUID()

    Given path 'risk'
    And request { transactionId: '#(txId)', customerId: 'c-1', amountCents: 200000 }
    When method POST
    Then status 200
    And match response.decision == 'DECLINE'
    * def correlationId = response.correlationId

    # Consume from Kafka — wait up to 5s for 1 event
    * def records = KafkaSteps.consume(kafkaBroker, kafkaTopic, 1, 5000)
    * def event = records[0]

    * match event.eventId == '#string'
    * match event.correlationId == correlationId
    * match event.eventVersion == 1
    * match event.decision == 'DECLINE'
    * match event.transactionId == txId
    * match event.amountCents == 200000

  Scenario: APPROVE decision also produces a Kafka event
    * def txId = 'kafka-tx-approve-' + java.util.UUID.randomUUID()

    Given path 'risk'
    And request { transactionId: '#(txId)', customerId: 'c-1', amountCents: 500 }
    When method POST
    Then status 200
    And match response.decision == 'APPROVE'

    * def records = KafkaSteps.consume(kafkaBroker, kafkaTopic, 1, 5000)
    * def event = records[0]
    * match event.decision == 'APPROVE'
    * match event.eventVersion == 1
