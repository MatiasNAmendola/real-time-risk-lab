package config

import (
	"os"
)

// Config holds all runtime configuration for risk-smoke.
type Config struct {
	ControllerURL  string
	KafkaBroker    string
	OpenObserveURL string
	KafkaTopic     string
	BaseURL        string
	OnlyChecks     []string
	Headless       bool
	CI             bool

	// Kafka smoke configuration.
	KafkaDockerImage   string // image used by the cp-kafka smoke path
	KafkaDockerNetwork string // docker network where Tansu is reachable
	KafkaDockerBroker  string // broker address from inside KafkaDockerNetwork

	// Reporting
	OutDir         string // base directory for file reports (default: out/smoke/)
	NoFileReport   bool   // disable file output (console only)
	ReportOnlyFail bool   // only write file report for failed checks
}

// Default returns config populated from env vars or hard-coded defaults.
func Default() *Config {
	cfg := &Config{
		ControllerURL:      "http://localhost:8080",
		KafkaBroker:        "localhost:9092",
		OpenObserveURL:     "http://localhost:5080",
		KafkaTopic:         "risk-decisions",
		KafkaDockerImage:   "confluentinc/cp-kafka:7.0.0",
		KafkaDockerNetwork: "compose_data-net",
		KafkaDockerBroker:  "tansu:9092",
	}
	if v := os.Getenv("RISK_SMOKE_CONTROLLER_URL"); v != "" {
		cfg.ControllerURL = v
	}
	if v := os.Getenv("RISK_SMOKE_KAFKA_BROKER"); v != "" {
		cfg.KafkaBroker = v
	}
	if v := os.Getenv("RISK_SMOKE_OPENOBSERVE_URL"); v != "" {
		cfg.OpenObserveURL = v
	}
	if v := os.Getenv("RISK_SMOKE_KAFKA_TOPIC"); v != "" {
		cfg.KafkaTopic = v
	}
	if v := os.Getenv("RISK_SMOKE_KAFKA_DOCKER_IMAGE"); v != "" {
		cfg.KafkaDockerImage = v
	}
	if v := os.Getenv("RISK_SMOKE_KAFKA_DOCKER_NETWORK"); v != "" {
		cfg.KafkaDockerNetwork = v
	}
	if v := os.Getenv("RISK_SMOKE_KAFKA_DOCKER_BROKER"); v != "" {
		cfg.KafkaDockerBroker = v
	}
	// BaseURL mirrors ControllerURL but can be overridden independently.
	cfg.BaseURL = cfg.ControllerURL
	return cfg
}
