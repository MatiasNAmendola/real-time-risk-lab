package riskclient

import (
	"bufio"
	"context"
	"encoding/json"
	"strings"
)

// StreamClient provides SSE streaming.
type StreamClient struct {
	http   *jsonHTTP
	sseURL string
}

func newStreamClient(cfg Config, h *jsonHTTP) *StreamClient {
	return &StreamClient{
		http:   h,
		sseURL: envMap[cfg.Environment].restBaseURL + "/risk/stream",
	}
}

// Decisions opens the SSE stream and invokes handler for each DecisionEvent
// until ctx is cancelled or the stream closes.
func (s *StreamClient) Decisions(ctx context.Context, handler DecisionHandler) error {
	body, err := s.http.openSSEStream(ctx, s.sseURL)
	if err != nil {
		return err
	}
	defer body.Close()

	scanner := bufio.NewScanner(body)
	for scanner.Scan() {
		select {
		case <-ctx.Done():
			return ctx.Err()
		default:
		}

		line := scanner.Text()
		if !strings.HasPrefix(line, "data:") {
			continue
		}
		data := strings.TrimPrefix(line, "data:")
		data = strings.TrimSpace(data)

		var event DecisionEvent
		if err := json.Unmarshal([]byte(data), &event); err != nil {
			continue // skip malformed event
		}
		if err := handler(ctx, event); err != nil {
			return err
		}
	}
	return scanner.Err()
}
