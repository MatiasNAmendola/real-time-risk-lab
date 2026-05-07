package riskclient

import (
	"context"
	"encoding/json"

	"github.com/twmb/franz-go/pkg/kgo"
)

const (
	decisionsTopicName    = "risk-decisions"
	customEventsTopicName = "risk-custom-events"
)

// EventsClient encapsulates Kafka operations.
type EventsClient struct {
	broker string
}

func newEventsClient(cfg Config) *EventsClient {
	return &EventsClient{broker: envMap[cfg.Environment].kafkaBroker}
}

// ConsumeDecisions subscribes to the decisions topic and calls handler for
// each event. Blocks until ctx is cancelled.
func (e *EventsClient) ConsumeDecisions(ctx context.Context, groupID string, handler DecisionHandler) error {
	cl, err := kgo.NewClient(
		kgo.SeedBrokers(e.broker),
		kgo.ConsumerGroup(groupID),
		kgo.ConsumeTopics(decisionsTopicName),
		kgo.ConsumeResetOffset(kgo.NewOffset().AtEnd()),
	)
	if err != nil {
		return &RiskClientError{Message: "kafka client init", Cause: err}
	}
	defer cl.Close()

	for {
		select {
		case <-ctx.Done():
			return ctx.Err()
		default:
		}

		fetches := cl.PollFetches(ctx)
		if errs := fetches.Errors(); len(errs) > 0 {
			if ctx.Err() != nil {
				return ctx.Err()
			}
			return &RiskClientError{Message: "kafka poll", Cause: errs[0].Err}
		}

		var handlerErr error
		fetches.EachRecord(func(r *kgo.Record) {
			if handlerErr != nil {
				return
			}
			var event DecisionEvent
			if jsonErr := json.Unmarshal(r.Value, &event); jsonErr == nil {
				handlerErr = handler(ctx, event)
			}
		})
		if handlerErr != nil {
			return handlerErr
		}
	}
}

// PublishCustomEvent serializes envelope and produces it to the custom events topic.
func (e *EventsClient) PublishCustomEvent(ctx context.Context, envelope map[string]any) error {
	cl, err := kgo.NewClient(kgo.SeedBrokers(e.broker))
	if err != nil {
		return &RiskClientError{Message: "kafka producer init", Cause: err}
	}
	defer cl.Close()

	b, err := json.Marshal(envelope)
	if err != nil {
		return &RiskClientError{Message: "marshal envelope", Cause: err}
	}

	results := cl.ProduceSync(ctx, &kgo.Record{
		Topic: customEventsTopicName,
		Value: b,
	})
	for _, res := range results {
		if res.Err != nil {
			return &RiskClientError{Message: "kafka produce", Cause: res.Err}
		}
	}
	return nil
}
