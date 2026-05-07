package config_test

import (
	"os"
	"testing"

	"github.com/naranjax/risk-smoke/internal/config"
)

func TestDefault_Defaults(t *testing.T) {
	cfg := config.Default()
	if cfg.ControllerURL != "http://localhost:8080" {
		t.Errorf("expected default ControllerURL, got %q", cfg.ControllerURL)
	}
	if cfg.KafkaBroker != "localhost:19092" {
		t.Errorf("expected default KafkaBroker, got %q", cfg.KafkaBroker)
	}
	if cfg.KafkaTopic != "risk-decisions" {
		t.Errorf("expected default KafkaTopic, got %q", cfg.KafkaTopic)
	}
	if cfg.OpenObserveURL != "http://localhost:5080" {
		t.Errorf("expected default OpenObserveURL, got %q", cfg.OpenObserveURL)
	}
}

func TestDefault_EnvOverride(t *testing.T) {
	os.Setenv("RISK_SMOKE_CONTROLLER_URL", "http://staging:9090")
	os.Setenv("RISK_SMOKE_KAFKA_BROKER", "kafka-staging:9092")
	defer func() {
		os.Unsetenv("RISK_SMOKE_CONTROLLER_URL")
		os.Unsetenv("RISK_SMOKE_KAFKA_BROKER")
	}()

	cfg := config.Default()
	if cfg.ControllerURL != "http://staging:9090" {
		t.Errorf("expected env override, got %q", cfg.ControllerURL)
	}
	if cfg.KafkaBroker != "kafka-staging:9092" {
		t.Errorf("expected env override, got %q", cfg.KafkaBroker)
	}
}
