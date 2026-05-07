@webhook
Feature: Webhook callback on DECLINE decisions

  # The controller-app supports a webhook registry.  When a consumer registers
  # a callback URL with filter=DECLINE, the service POSTs the decision payload
  # to that URL whenever a DECLINE is produced.

  Background:
    * url baseUrl

  Scenario: Register a DECLINE webhook and receive a callback for a high-amount transaction
    # 1. Start the local listener on a random port
    * def WebhookListener = Java.type('com.naranjax.atdd.support.WebhookListener')
    * def listener = new WebhookListener()
    * def listenerPort = listener.start()
    * def callbackUrl = 'http://' + webhookListenerHost + ':' + listenerPort + '/callback'

    # 2. Register the webhook
    Given path 'webhooks'
    And request { callbackUrl: '#(callbackUrl)', filter: 'DECLINE' }
    When method POST
    Then status 201
    And match response.webhookId == '#string'
    * def webhookId = response.webhookId

    # 3. Trigger a DECLINE decision
    Given path 'risk'
    And request { transactionId: 'wh-tx-decline-1', customerId: 'c-1', amountCents: 200000 }
    When method POST
    Then status 200
    And match response.decision == 'DECLINE'

    # 4. Wait for the callback (up to 3 s)
    * def payload = listener.awaitCallback(3000)
    * match payload.decision == 'DECLINE'
    * match payload.transactionId == 'wh-tx-decline-1'
    * match payload.webhookId == webhookId

    # 5. Cleanup
    * listener.stop()

  Scenario: An APPROVE decision does NOT trigger a DECLINE-filtered webhook
    * def WebhookListener = Java.type('com.naranjax.atdd.support.WebhookListener')
    * def listener = new WebhookListener()
    * def listenerPort = listener.start()
    * def callbackUrl = 'http://' + webhookListenerHost + ':' + listenerPort + '/callback'

    Given path 'webhooks'
    And request { callbackUrl: '#(callbackUrl)', filter: 'DECLINE' }
    When method POST
    Then status 201

    Given path 'risk'
    And request { transactionId: 'wh-tx-approve-1', customerId: 'c-1', amountCents: 500 }
    When method POST
    Then status 200
    And match response.decision == 'APPROVE'

    # Callback should NOT arrive — wait 1s and assert nothing was received
    * def noCallback = function(){ try { listener.awaitCallback(1000); return false; } catch(e){ return true; } }
    * match noCallback() == true

    * listener.stop()
