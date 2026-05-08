package flows

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"

	"github.com/riskplatform/risk-smoke/internal/config"
)

func postSmokeRisk(cfg *config.Config, suffix string, amountCents int64) (int, string, error) {
	payload, _ := json.Marshal(map[string]interface{}{
		"transactionId":  fmt.Sprintf("%s-%d", suffix, time.Now().UnixNano()),
		"customerId":     fmt.Sprintf("u-%s", suffix),
		"amountCents":    amountCents,
		"correlationId":  fmt.Sprintf("corr-%s-%d", suffix, time.Now().UnixNano()),
		"idempotencyKey": fmt.Sprintf("idem-%s-%d", suffix, time.Now().UnixNano()),
	})
	client := &http.Client{Timeout: 10 * time.Second}
	resp, err := client.Post(cfg.BaseURL+"/risk", "application/json", bytes.NewReader(payload))
	if err != nil {
		return 0, "", err
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(io.LimitReader(resp.Body, 4096))
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return resp.StatusCode, string(body), fmt.Errorf("POST /risk returned HTTP %d: %s", resp.StatusCode, truncate(string(body), 200))
	}
	return resp.StatusCode, string(body), nil
}
