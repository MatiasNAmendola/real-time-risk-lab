package riskclient

import "context"

// EventsClient exposes asynchronous decision events without using Kafka wire
// directly from Go. Against local Tansu 0.6.0, Go Kafka consumer groups hang in
// Fetch (tansu-io/tansu#668), so the Go SDK routes consumption through the
// supported HTTP/SSE adapter and leaves Kafka wire to JVM cp-kafka clients.
type EventsClient struct {
	stream  *StreamClient
	http    *jsonHTTP
	baseURL string
}

func newEventsClient(cfg Config, h *jsonHTTP) *EventsClient {
	return &EventsClient{
		stream:  newStreamClient(cfg, h),
		http:    h,
		baseURL: envMap[cfg.Environment].restBaseURL,
	}
}

// ConsumeDecisions subscribes to the decision event stream and calls handler for
// each event. groupID is accepted for API parity with Java/TypeScript SDKs, but
// the Go adapter is SSE-backed and does not join a Kafka consumer group.
func (e *EventsClient) ConsumeDecisions(ctx context.Context, groupID string, handler DecisionHandler) error {
	_ = groupID
	return e.stream.Decisions(ctx, handler)
}

// PublishCustomEvent posts an event envelope to the HTTP event-ingress adapter.
// The server side can fan this into Kafka with a supported JVM client.
func (e *EventsClient) PublishCustomEvent(ctx context.Context, envelope map[string]any) error {
	return e.http.postJSON(ctx, e.baseURL+"/events/custom", envelope, nil)
}
