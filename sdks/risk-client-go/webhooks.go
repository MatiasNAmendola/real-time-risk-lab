package riskclient

import (
	"context"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
)

// WebhooksClient manages webhook subscriptions.
type WebhooksClient struct {
	http    *jsonHTTP
	baseURL string
}

func newWebhooksClient(cfg Config, h *jsonHTTP) *WebhooksClient {
	return &WebhooksClient{http: h, baseURL: envMap[cfg.Environment].restBaseURL}
}

// Subscribe registers callbackURL for the given event filter (e.g. "DECLINE,REVIEW").
func (w *WebhooksClient) Subscribe(ctx context.Context, callbackURL, eventFilter string) (*Subscription, error) {
	body := map[string]any{
		"callbackUrl": callbackURL,
		"events":      splitFilter(eventFilter),
	}
	var sub Subscription
	if err := w.http.postJSON(ctx, w.baseURL+"/webhook/register", body, &sub); err != nil {
		return nil, err
	}
	return &sub, nil
}

// Unsubscribe removes a subscription by ID.
func (w *WebhooksClient) Unsubscribe(ctx context.Context, subscriptionID string) error {
	url := fmt.Sprintf("%s/webhook/unregister/%s", w.baseURL, subscriptionID)
	return w.http.postJSON(ctx, url, map[string]any{}, nil)
}

// List returns all subscriptions for this API key.
func (w *WebhooksClient) List(ctx context.Context) ([]Subscription, error) {
	var subs []Subscription
	if err := w.http.getJSON(ctx, w.baseURL+"/webhook/subscriptions", &subs); err != nil {
		return nil, err
	}
	return subs, nil
}

// Verify checks that payload matches the provided HMAC-SHA256 signature.
// Uses constant-time comparison.
func (w *WebhooksClient) Verify(payload []byte, signature, signingSecret string) bool {
	mac := hmac.New(sha256.New, []byte(signingSecret))
	mac.Write(payload)
	expected := hex.EncodeToString(mac.Sum(nil))

	sigBytes, err := hex.DecodeString(signature)
	if err != nil {
		return false
	}
	expBytes, _ := hex.DecodeString(expected)
	return hmac.Equal(expBytes, sigBytes)
}

func splitFilter(filter string) []string {
	if filter == "" {
		return nil
	}
	var parts []string
	start := 0
	for i := 0; i < len(filter); i++ {
		if filter[i] == ',' {
			parts = append(parts, filter[start:i])
			start = i + 1
		}
	}
	parts = append(parts, filter[start:])
	return parts
}
