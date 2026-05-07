@websocket
Feature: WebSocket bidirectional risk evaluation

  # The controller-app exposes ws://localhost:8080/risk/ws for real-time
  # bidirectional communication.  Each message sent is a transaction payload;
  # each reply is a RiskDecision.  A broadcast message is sent when a DECLINE
  # decision is produced (so all connected clients are notified).

  Background:
    * def wsUrl = baseUrl.replace('http://', 'ws://') + '/risk/ws'

  Scenario: Send 3 transactions and receive 3 individual decisions plus 1 broadcast
    * def socket = karate.webSocket(wsUrl)

    # Transaction 1: low amount → APPROVE
    * def tx1 = { transactionId: 'ws-tx-1', customerId: 'c-1', amountCents: 500 }
    * socket.send(karate.toJson(tx1))
    * def resp1 = socket.listen(3000)
    * def d1 = karate.fromJson(resp1)
    * match d1.decision == 'APPROVE'
    * match d1.transactionId == 'ws-tx-1'

    # Transaction 2: mid amount → REVIEW
    * def tx2 = { transactionId: 'ws-tx-2', customerId: 'c-1', amountCents: 55000 }
    * socket.send(karate.toJson(tx2))
    * def resp2 = socket.listen(3000)
    * def d2 = karate.fromJson(resp2)
    * match d2.decision == 'REVIEW'

    # Transaction 3: high amount → DECLINE + broadcast
    * def tx3 = { transactionId: 'ws-tx-3', customerId: 'c-1', amountCents: 250000 }
    * socket.send(karate.toJson(tx3))
    * def resp3 = socket.listen(3000)
    * def d3 = karate.fromJson(resp3)
    * match d3.decision == 'DECLINE'

    # Broadcast notification (separate frame, same connection)
    * def broadcastRaw = socket.listen(3000)
    * def broadcast = karate.fromJson(broadcastRaw)
    * match broadcast.type == 'BROADCAST'
    * match broadcast.decision == 'DECLINE'

    * socket.close()
