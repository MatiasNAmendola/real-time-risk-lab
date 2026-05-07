package flows

import (
	"context"
	"encoding/json"
	"fmt"
	"strings"
	"time"

	"github.com/coder/websocket"
	"github.com/coder/websocket/wsjson"
	"github.com/naranjax/risk-smoke/internal/config"
)

var wsPayloads = []map[string]interface{}{
	{"transactionId": "ws-smoke-001", "customerId": "u-ws-001", "amountCents": 1000, "correlationId": "corr-ws-001"},
	{"transactionId": "ws-smoke-002", "customerId": "u-ws-002", "amountCents": 75000, "correlationId": "corr-ws-002"},
	{"transactionId": "ws-smoke-003", "customerId": "u-ws-003", "amountCents": 200000, "correlationId": "corr-ws-003"},
}

// WebSocketCheck sends 3 transactions over WS and reads back responses.
type WebSocketCheck struct{}

func (c *WebSocketCheck) ID() string   { return CheckWebSocket }
func (c *WebSocketCheck) Name() string { return "WebSocket bidi — /ws/risk" }

func (c *WebSocketCheck) Run(cfg *config.Config) Result {
	wsURL := strings.Replace(cfg.BaseURL, "http://", "ws://", 1)
	wsURL = strings.Replace(wsURL, "https://", "wss://", 1)
	wsURL += "/ws/risk"

	req := fmt.Sprintf("WS %s, 3 messages", wsURL)

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	conn, _, err := websocket.Dial(ctx, wsURL, nil)
	if err != nil {
		return Result{
			ID:      CheckWebSocket,
			Passed:  false,
			Request: req,
			ErrMsg:  fmt.Sprintf("dial error: %v", err),
			Detail:  fmt.Sprintf("FAILED — %v", err),
		}
	}
	defer conn.CloseNow()

	var sent []string
	var received []string

	// Send 3 messages
	for _, payload := range wsPayloads {
		b, _ := json.Marshal(payload)
		sent = append(sent, string(b))
		if err := wsjson.Write(ctx, conn, payload); err != nil {
			return Result{
				ID:      CheckWebSocket,
				Passed:  false,
				Request: req,
				ErrMsg:  fmt.Sprintf("write error: %v", err),
				Detail:  fmt.Sprintf("FAILED — write error: %v", err),
			}
		}
	}

	// Read up to 5 responses (3 replies + possible broadcasts)
	for i := 0; i < 5; i++ {
		readCtx, readCancel := context.WithTimeout(ctx, 2*time.Second)
		var resp map[string]interface{}
		err := wsjson.Read(readCtx, conn, &resp)
		readCancel()
		if err != nil {
			break
		}
		b, _ := json.Marshal(resp)
		received = append(received, string(b))
		if len(received) >= 3 {
			break
		}
	}

	conn.Close(websocket.StatusNormalClosure, "smoke done")

	if len(received) == 0 {
		return Result{
			ID:      CheckWebSocket,
			Passed:  false,
			Request: req,
			ErrMsg:  "no responses received",
			Detail:  "FAILED — sent 3, received 0 responses",
		}
	}

	return Result{
		ID:       CheckWebSocket,
		Passed:   len(received) >= 3,
		Request:  fmt.Sprintf("WS %s\nSent: %s", wsURL, strings.Join(sent, "\n")),
		Response: strings.Join(received, "\n"),
		Detail:   fmt.Sprintf("PASSED — sent 3, received %d responses", len(received)),
	}
}
