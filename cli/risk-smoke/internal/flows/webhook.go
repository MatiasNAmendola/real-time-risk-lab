package flows

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"net/http"
	"time"

	"github.com/riskplatform/risk-smoke/internal/config"
)

// WebhookCheck registers a local HTTP listener as webhook, fires a high-amount
// transaction (DECLINE trigger) and waits for the callback.
type WebhookCheck struct{}

func (c *WebhookCheck) ID() string   { return CheckWebhook }
func (c *WebhookCheck) Name() string { return "Webhook — register + callback" }

func (c *WebhookCheck) Run(cfg *config.Config) Result {
	// 1. Bind a random local port.
	ln, err := net.Listen("tcp", "0.0.0.0:0")
	if err != nil {
		return Result{
			ID:     CheckWebhook,
			Passed: false,
			ErrMsg: fmt.Sprintf("could not bind listener: %v", err),
			Detail: fmt.Sprintf("FAILED — %v", err),
		}
	}
	_, port, _ := net.SplitHostPort(ln.Addr().String())
	localAddr := fmt.Sprintf("http://host.docker.internal:%s/webhook", port)
	req := fmt.Sprintf("localListener=%s, registerURL=%s/webhook/register", localAddr, cfg.BaseURL)

	received := make(chan string, 1)

	srv := &http.Server{
		Handler: http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			body, _ := io.ReadAll(io.LimitReader(r.Body, 4096))
			r.Body.Close()
			select {
			case received <- string(body):
			default:
			}
			w.WriteHeader(http.StatusOK)
		}),
	}

	go func() { _ = srv.Serve(ln) }()
	defer func() {
		ctx, cancel := context.WithTimeout(context.Background(), time.Second)
		defer cancel()
		_ = srv.Shutdown(ctx)
	}()

	// 2. Register webhook.
	regURL := cfg.BaseURL + "/webhooks"
	regBody, _ := json.Marshal(map[string]interface{}{
		"url":    localAddr,
		"filter": "DECLINE,REVIEW",
	})
	regResp, err := http.Post(regURL, "application/json", bytes.NewReader(regBody))
	if err != nil {
		return Result{
			ID:      CheckWebhook,
			Passed:  false,
			Request: req,
			ErrMsg:  fmt.Sprintf("register error: %v", err),
			Detail:  fmt.Sprintf("FAILED — webhook register error: %v", err),
		}
	}
	regResp.Body.Close()

	if regResp.StatusCode != http.StatusOK && regResp.StatusCode != http.StatusCreated {
		return Result{
			ID:       CheckWebhook,
			Passed:   false,
			Request:  req,
			Response: fmt.Sprintf("register HTTP %d", regResp.StatusCode),
			ErrMsg:   fmt.Sprintf("register returned %d", regResp.StatusCode),
			Detail:   fmt.Sprintf("FAILED — register HTTP %d", regResp.StatusCode),
		}
	}

	// 3. Fire transaction likely to trigger webhook fanout.
	status, body, err := postSmokeRisk(cfg, "webhook-smoke", 200000)
	if err != nil {
		return Result{
			ID:      CheckWebhook,
			Passed:  false,
			Request: req,
			ErrMsg:  fmt.Sprintf("transaction error: %v", err),
			Detail:  fmt.Sprintf("FAILED — transaction error: %v", err),
		}
	}
	_ = status
	_ = body

	// 4. Wait for callback up to 3s.
	select {
	case payload := <-received:
		return Result{
			ID:       CheckWebhook,
			Passed:   true,
			Request:  req,
			Response: fmt.Sprintf("callback payload: %s", truncate(payload, 200)),
			Detail:   "PASSED — callback received within 3s",
		}
	case <-time.After(3 * time.Second):
		return Result{
			ID:      CheckWebhook,
			Passed:  false,
			Request: req,
			ErrMsg:  "no callback received within 3s",
			Detail:  "FAILED — webhook callback timeout (3s)",
		}
	}
}
