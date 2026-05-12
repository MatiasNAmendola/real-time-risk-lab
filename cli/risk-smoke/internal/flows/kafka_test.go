package flows

import (
	"reflect"
	"testing"

	"github.com/riskplatform/risk-smoke/internal/config"
)

func TestKafkaConsoleConsumerDockerArgs_DefaultTansuPath(t *testing.T) {
	cfg := config.Default()
	args := kafkaConsoleConsumerDockerArgs(cfg, cfg.KafkaDockerBroker)

	want := []string{
		"run", "--rm",
		"--network", "compose_data-net",
		"confluentinc/cp-kafka:7.0.0",
		"kafka-console-consumer",
		"--bootstrap-server", "tansu:9092",
		"--topic", "risk-decisions",
		"--from-beginning",
		"--timeout-ms", "7000",
		"--max-messages", "5",
	}
	if !reflect.DeepEqual(args, want) {
		t.Fatalf("unexpected docker args\nwant: %#v\n got: %#v", want, args)
	}
}

func TestKafkaConsoleConsumerDockerArgs_AllowsNoNetwork(t *testing.T) {
	cfg := config.Default()
	cfg.KafkaDockerNetwork = ""
	cfg.KafkaDockerImage = "custom/kafka:local"
	cfg.KafkaTopic = "custom-topic"

	args := kafkaConsoleConsumerDockerArgs(cfg, "host.docker.internal:9092")
	for _, arg := range args {
		if arg == "--network" {
			t.Fatalf("did not expect --network in args: %#v", args)
		}
	}
	if args[2] != "custom/kafka:local" {
		t.Fatalf("expected custom image at args[2], got %#v", args)
	}
}

func TestNonEmptyLines_TrimsAndTruncates(t *testing.T) {
	lines := nonEmptyLines("\n first \n\nsecond\n")
	want := []string{"first", "second"}
	if !reflect.DeepEqual(lines, want) {
		t.Fatalf("unexpected lines: %#v", lines)
	}
}
