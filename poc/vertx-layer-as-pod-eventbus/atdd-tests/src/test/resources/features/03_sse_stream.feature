@sse
Feature: SSE stream of risk decision events

  # The controller-app exposes GET /risk/stream as a Server-Sent Events feed.
  # After subscribing, any POST /risk triggers a "decision" event on the stream.
  #
  # NOTE: Karate's built-in SSE support is available via karate.listen().
  # This feature uses the low-level async approach with karate.listen + karate.signal.

  Background:
    * url baseUrl

  @wip
  Scenario: SSE stream emits a decision event after POST /risk
    # Start listening to the SSE endpoint
    * def StreamHelper = Java.type('com.intuit.karate.http.HttpUtils')
    # Karate does not have first-class SSE, but we can verify the endpoint exists
    # and emits text/event-stream content-type.
    Given path 'risk', 'stream'
    And header Accept = 'text/event-stream'
    When method GET
    # The connection is kept alive; Karate closes it after reading headers
    Then status 200
    And match responseHeaders['Content-Type'][0] contains 'text/event-stream'
    # TODO: Full SSE frame assertion requires a dedicated async listener.
    # Pattern: karate.fork({ url: baseUrl + '/risk/stream', ... })
    # then POST /risk and await the event.  Mark @wip until Karate async API is wired.
