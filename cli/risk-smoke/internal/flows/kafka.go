package flows

import (
	"context"
	"fmt"
	"strings"
	"time"

	"github.com/twmb/franz-go/pkg/kgo"
	"github.com/riskplatform/risk-smoke/internal/config"
)

// KafkaCheck consumes up to 5 messages from the risk-decisions topic.
type KafkaCheck struct{}

func (c *KafkaCheck) ID() string   { return CheckKafka }
func (c *KafkaCheck) Name() string { return "Kafka — topic risk-decisions" }

func (c *KafkaCheck) Run(cfg *config.Config) Result {
	req := fmt.Sprintf("broker=%s topic=%s group=smoke-ephemeral-%d", cfg.KafkaBroker, cfg.KafkaTopic, time.Now().UnixNano())

	groupID := fmt.Sprintf("risk-smoke-%d", time.Now().UnixNano())

	cl, err := kgo.NewClient(
		kgo.SeedBrokers(cfg.KafkaBroker),
		kgo.ConsumerGroup(groupID),
		kgo.ConsumeTopics(cfg.KafkaTopic),
		kgo.ConsumeResetOffset(kgo.NewOffset().AtEnd()),
	)
	if err != nil {
		return Result{
			ID:      CheckKafka,
			Passed:  false,
			Request: req,
			ErrMsg:  fmt.Sprintf("client init error: %v", err),
			Detail:  fmt.Sprintf("FAILED — %v", err),
		}
	}
	defer cl.Close()

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	var messages []string
	for len(messages) < 5 {
		fetches := cl.PollFetches(ctx)
		if errs := fetches.Errors(); len(errs) > 0 {
			var errStrs []string
			for _, e := range errs {
				errStrs = append(errStrs, e.Err.Error())
			}
			if ctx.Err() != nil {
				break
			}
			return Result{
				ID:      CheckKafka,
				Passed:  false,
				Request: req,
				ErrMsg:  strings.Join(errStrs, "; "),
				Detail:  fmt.Sprintf("FAILED — %s", errStrs[0]),
			}
		}

		fetches.EachRecord(func(r *kgo.Record) {
			if len(messages) < 5 {
				messages = append(messages, truncate(string(r.Value), 80))
			}
		})

		if ctx.Err() != nil {
			break
		}
	}

	if len(messages) == 0 {
		return Result{
			ID:      CheckKafka,
			Passed:  false,
			Request: req,
			ErrMsg:  "no messages received within 5s (topic may be empty or broker unreachable)",
			Detail:  "FAILED — no messages in 5s",
		}
	}

	return Result{
		ID:       CheckKafka,
		Passed:   true,
		Request:  req,
		Response: fmt.Sprintf("%d messages:\n%s", len(messages), strings.Join(messages, "\n")),
		Detail:   fmt.Sprintf("PASSED — %d messages consumed", len(messages)),
	}
}
