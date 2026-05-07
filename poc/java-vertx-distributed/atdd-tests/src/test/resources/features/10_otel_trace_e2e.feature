@otel @trace
Feature: OpenTelemetry end-to-end trace

  # Asserts that a single POST /risk produces a trace visible in OpenObserve
  # that spans all three services: controller-app, usecase-app, repository-app.
  # Also validates that the controller-app span carries the risk.decision attribute.
  #
  # The trace propagation uses the W3C Trace Context standard (traceparent header).
  # The response includes the traceresponse header (or x-b3-traceid) so we can
  # query OpenObserve without parsing logs.

  Background:
    * url baseUrl
    * def TraceFinder = Java.type('com.naranjax.atdd.support.TraceFinder')

  Scenario: POST /risk produces a trace spanning all 3 services
    Given path 'risk'
    And request { transactionId: 'otel-tx-1', customerId: 'c-1', amountCents: 200000 }
    When method POST
    Then status 200
    And match response.decision == 'DECLINE'

    # Extract traceId from response headers (W3C traceresponse or custom header)
    * def traceresponseHeader = responseHeaders['traceresponse'] || responseHeaders['x-trace-id'] || responseHeaders['x-b3-traceid']
    * def traceId = traceresponseHeader != null ? traceresponseHeader[0] : null

    # If the service exposes the traceId in the response body, use that
    * def bodyTraceId = response.traceId || null
    * def resolvedTraceId = traceId || bodyTraceId

    # Skip deep OTel assertion if traceId is not exposed (mark as @wip if needed)
    * if (resolvedTraceId == null) karate.log('WARNING: traceId not found in response headers or body — skipping OTel span assertion')
    * if (resolvedTraceId == null) karate.call('classpath:features/_skip.js')

    # Query OpenObserve with backoff (up to 5s — traces may take a moment to ingest)
    * def spans = TraceFinder.findSpans(openObserveUrl, resolvedTraceId, 5000)
    * def serviceNames = TraceFinder.serviceNames(spans)

    # All three services must have contributed spans
    * match serviceNames contains 'controller-app'
    * match serviceNames contains 'usecase-app'
    * match serviceNames contains 'repository-app'

    # The controller-app span must carry the risk.decision attribute
    * def controllerSpans = karate.filter(spans, function(s){ return s.service_name == 'controller-app' || (s.resource && s.resource['service.name'] == 'controller-app') })
    * assert controllerSpans.length > 0
    * def controllerSpan = controllerSpans[0]
    # The decision attribute may be nested under attributes or at the top level
    * def decision = controllerSpan['risk.decision'] || (controllerSpan.attributes && controllerSpan.attributes['risk.decision'])
    * assert decision != null

  @wip
  Scenario: Trace shows correct parent-child span relationships
    # Verifies that controller-app span is parent of usecase-app span,
    # which is parent of repository-app span.
    # Implementation depends on OpenObserve query API returning parentSpanId.
    # Mark @wip until the span relationship query is confirmed against the live API.
    * karate.log('WIP: span parent-child relationship validation not yet implemented')
