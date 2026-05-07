package riskclient

import (
	"context"
	"encoding/json"
	"fmt"
	"strings"
	"time"

	"github.com/coder/websocket"
	"github.com/coder/websocket/wsjson"
)

// ChannelClient creates WebSocket connections.
type ChannelClient struct {
	wsURL  string
	apiKey string
}

func newChannelClient(cfg Config) *ChannelClient {
	base := envMap[cfg.Environment].restBaseURL
	base = strings.ReplaceAll(base, "https://", "wss://")
	base = strings.ReplaceAll(base, "http://", "ws://")
	return &ChannelClient{wsURL: base + "/ws/risk", apiKey: cfg.APIKey}
}

// RiskChannel is a live bidirectional WebSocket connection.
type RiskChannel struct {
	conn *websocket.Conn
}

// Open dials the WebSocket endpoint and returns a ready channel.
func (c *ChannelClient) Open(ctx context.Context) (*RiskChannel, error) {
	conn, _, err := websocket.Dial(ctx, c.wsURL, &websocket.DialOptions{
		HTTPHeader: map[string][]string{"X-API-Key": {c.apiKey}},
	})
	if err != nil {
		return nil, &RiskClientError{Message: fmt.Sprintf("WebSocket dial %s", c.wsURL), Cause: err}
	}
	return &RiskChannel{conn: conn}, nil
}

// Send serializes req and writes it to the WebSocket.
func (ch *RiskChannel) Send(ctx context.Context, req RiskRequest) error {
	return wsjson.Write(ctx, ch.conn, req)
}

// Receive reads one message and deserializes it as a RiskDecision.
func (ch *RiskChannel) Receive(ctx context.Context, timeoutMs int) (*RiskDecision, error) {
	readCtx, cancel := context.WithTimeout(ctx, time.Duration(timeoutMs)*time.Millisecond)
	defer cancel()

	var raw json.RawMessage
	if err := wsjson.Read(readCtx, ch.conn, &raw); err != nil {
		return nil, err
	}
	var dec RiskDecision
	if err := json.Unmarshal(raw, &dec); err != nil {
		return nil, err
	}
	return &dec, nil
}

// Close shuts down the WebSocket connection gracefully.
func (ch *RiskChannel) Close() {
	_ = ch.conn.Close(websocket.StatusNormalClosure, "client close")
}
