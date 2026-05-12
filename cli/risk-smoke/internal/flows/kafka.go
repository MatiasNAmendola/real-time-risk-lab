package flows

import (
	"bytes"
	"context"
	"fmt"
	"os/exec"
	"strings"
	"time"

	"github.com/riskplatform/risk-smoke/internal/config"
)

var execCommandContext = exec.CommandContext

// KafkaCheck consumes up to 5 messages from the risk-decisions topic.
// It shells out to Confluent cp-kafka 7.x because Tansu 0.6.0 has an upstream
// Fetch hang with Go Kafka wire clients such as franz-go consumer groups
// (tansu-io/tansu#668).
type KafkaCheck struct{}

func (c *KafkaCheck) ID() string   { return CheckKafka }
func (c *KafkaCheck) Name() string { return "Kafka — topic risk-decisions" }

func (c *KafkaCheck) Run(cfg *config.Config) Result {
	started := time.Now()
	broker := cfg.KafkaDockerBroker
	if broker == "" {
		broker = cfg.KafkaBroker
	}
	req := fmt.Sprintf("client=cp-kafka image=%s network=%s broker=%s topic=%s", cfg.KafkaDockerImage, cfg.KafkaDockerNetwork, broker, cfg.KafkaTopic)

	if _, _, err := postSmokeRisk(cfg, "kafka-smoke", 75000); err != nil {
		return Result{
			ID:        CheckKafka,
			Passed:    false,
			Request:   req,
			ErrMsg:    fmt.Sprintf("trigger transaction error: %v", err),
			Detail:    fmt.Sprintf("FAILED — trigger transaction error: %v", err),
			StartedAt: started,
			Duration:  time.Since(started),
		}
	}

	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()

	args := kafkaConsoleConsumerDockerArgs(cfg, broker)
	cmd := execCommandContext(ctx, "docker", args...)
	var stdout, stderr bytes.Buffer
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr

	err := cmd.Run()
	out := strings.TrimSpace(stdout.String())
	errOut := strings.TrimSpace(stderr.String())
	if err != nil && out == "" {
		msg := strings.TrimSpace(strings.Join([]string{err.Error(), errOut}, ": "))
		return Result{
			ID:        CheckKafka,
			Passed:    false,
			Request:   req,
			Response:  errOut,
			ErrMsg:    fmt.Sprintf("cp-kafka consumer failed: %s", msg),
			Detail:    "FAILED — cp-kafka consumer could not read Tansu topic",
			StartedAt: started,
			Duration:  time.Since(started),
		}
	}

	messages := nonEmptyLines(out)
	if len(messages) == 0 {
		return Result{
			ID:        CheckKafka,
			Passed:    false,
			Request:   req,
			Response:  errOut,
			ErrMsg:    "cp-kafka consumer read no messages; verify topic seeding, publisher logs, and Floci/S3 persistence",
			Detail:    "FAILED — no Kafka messages read by cp-kafka",
			StartedAt: started,
			Duration:  time.Since(started),
		}
	}

	if len(messages) > 5 {
		messages = messages[:5]
	}
	return Result{
		ID:        CheckKafka,
		Passed:    true,
		Request:   req,
		Response:  fmt.Sprintf("%d messages:\n%s", len(messages), strings.Join(messages, "\n")),
		Detail:    fmt.Sprintf("PASSED — %d messages consumed with cp-kafka 7.x", len(messages)),
		Duration:  time.Since(started),
		StartedAt: started,
	}
}

func kafkaConsoleConsumerDockerArgs(cfg *config.Config, broker string) []string {
	args := []string{"run", "--rm"}
	if cfg.KafkaDockerNetwork != "" {
		args = append(args, "--network", cfg.KafkaDockerNetwork)
	}
	args = append(args,
		cfg.KafkaDockerImage,
		"kafka-console-consumer",
		"--bootstrap-server", broker,
		"--topic", cfg.KafkaTopic,
		"--from-beginning",
		"--timeout-ms", "7000",
		"--max-messages", "5",
	)
	return args
}

func nonEmptyLines(s string) []string {
	var lines []string
	for _, line := range strings.Split(s, "\n") {
		line = strings.TrimSpace(line)
		if line != "" {
			lines = append(lines, truncate(line, 160))
		}
	}
	return lines
}
