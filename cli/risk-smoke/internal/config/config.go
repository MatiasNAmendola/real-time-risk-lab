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

	// Reporting
	OutDir         string // base directory for file reports (default: out/smoke/)
	NoFileReport   bool   // disable file output (console only)
	ReportOnlyFail bool   // only write file report for failed checks
}

// Default returns config populated from env vars or hard-coded defaults.
func Default() *Config {
	cfg := &Config{
		ControllerURL:  "http://localhost:8080",
		KafkaBroker:    "localhost:19092",
		OpenObserveURL: "http://localhost:5080",
		KafkaTopic:     "risk-decisions",
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
	// BaseURL mirrors ControllerURL but can be overridden independently.
	cfg.BaseURL = cfg.ControllerURL
	return cfg
}
