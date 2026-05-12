package config_test

import (
	"os"
	"testing"

	"github.com/riskplatform/risk-smoke/internal/config"
)

func TestDefault_Defaults(t *testing.T) {
	cfg := config.Default()
	if cfg.ControllerURL != "http://localhost:8080" {
		t.Errorf("expected default ControllerURL, got %q", cfg.ControllerURL)
	}
	if cfg.KafkaBroker != "localhost:9092" {
		t.Errorf("expected default KafkaBroker, got %q", cfg.KafkaBroker)
	}
	if cfg.KafkaTopic != "risk-decisions" {
		t.Errorf("expected default KafkaTopic, got %q", cfg.KafkaTopic)
	}
	if cfg.KafkaDockerNetwork != "compose_data-net" {
		t.Errorf("expected default KafkaDockerNetwork, got %q", cfg.KafkaDockerNetwork)
	}
	if cfg.KafkaDockerBroker != "tansu:9092" {
		t.Errorf("expected default KafkaDockerBroker, got %q", cfg.KafkaDockerBroker)
	}
	if cfg.OpenObserveURL != "http://localhost:5080" {
		t.Errorf("expected default OpenObserveURL, got %q", cfg.OpenObserveURL)
	}
}

func TestDefault_EnvOverride(t *testing.T) {
	os.Setenv("RISK_SMOKE_CONTROLLER_URL", "http://staging:9090")
	os.Setenv("RISK_SMOKE_KAFKA_BROKER", "kafka-staging:9092")
	os.Setenv("RISK_SMOKE_KAFKA_DOCKER_NETWORK", "custom_net")
	os.Setenv("RISK_SMOKE_KAFKA_DOCKER_BROKER", "kafka:9092")
	defer func() {
		os.Unsetenv("RISK_SMOKE_CONTROLLER_URL")
		os.Unsetenv("RISK_SMOKE_KAFKA_BROKER")
		os.Unsetenv("RISK_SMOKE_KAFKA_DOCKER_NETWORK")
		os.Unsetenv("RISK_SMOKE_KAFKA_DOCKER_BROKER")
	}()

	cfg := config.Default()
	if cfg.ControllerURL != "http://staging:9090" {
		t.Errorf("expected env override, got %q", cfg.ControllerURL)
	}
	if cfg.KafkaBroker != "kafka-staging:9092" {
		t.Errorf("expected env override, got %q", cfg.KafkaBroker)
	}
	if cfg.KafkaDockerNetwork != "custom_net" {
		t.Errorf("expected KafkaDockerNetwork env override, got %q", cfg.KafkaDockerNetwork)
	}
	if cfg.KafkaDockerBroker != "kafka:9092" {
		t.Errorf("expected KafkaDockerBroker env override, got %q", cfg.KafkaDockerBroker)
	}
}
