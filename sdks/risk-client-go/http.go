package riskclient

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"
)

// RiskClientError is returned for non-retryable failures.
type RiskClientError struct {
	StatusCode int
	Message    string
	Cause      error
}

func (e *RiskClientError) Error() string {
	if e.StatusCode > 0 {
		return fmt.Sprintf("risk client error: HTTP %d — %s", e.StatusCode, e.Message)
	}
	if e.Cause != nil {
		return fmt.Sprintf("risk client error: %s: %v", e.Message, e.Cause)
	}
	return "risk client error: " + e.Message
}

func (e *RiskClientError) Unwrap() error { return e.Cause }

// jsonHTTP is a thin HTTP client with retry.
type jsonHTTP struct {
	client *http.Client
	cfg    Config
}

func newJSONHTTP(cfg Config) *jsonHTTP {
	timeout := cfg.Timeout
	if timeout == 0 {
		timeout = 280 * time.Millisecond
	}
	return &jsonHTTP{
		client: &http.Client{Timeout: timeout},
		cfg:    cfg,
	}
}

func (j *jsonHTTP) postJSON(ctx context.Context, url string, body, result any) error {
	b, err := json.Marshal(body)
	if err != nil {
		return &RiskClientError{Message: "marshal request", Cause: err}
	}
	return j.doWithRetry(ctx, http.MethodPost, url, b, result)
}

func (j *jsonHTTP) getJSON(ctx context.Context, url string, result any) error {
	return j.doWithRetry(ctx, http.MethodGet, url, nil, result)
}

func (j *jsonHTTP) doWithRetry(ctx context.Context, method, url string, body []byte, result any) error {
	policy := j.cfg.Retry
	if policy.MaxAttempts == 0 {
		policy = ExponentialBackoff()
	}
	delayMs := policy.InitialDelay.Milliseconds()

	for attempt := 1; attempt <= policy.MaxAttempts; attempt++ {
		err := j.doOnce(ctx, method, url, body, result)
		if err == nil {
			return nil
		}
		var rce *RiskClientError
		if ok := isType(err, &rce); ok && rce.StatusCode >= 400 && rce.StatusCode < 500 {
			return err // client error — do not retry
		}
		if attempt == policy.MaxAttempts {
			return err
		}
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-time.After(time.Duration(delayMs) * time.Millisecond):
		}
		delayMs = int64(float64(delayMs) * policy.Multiplier)
	}
	return &RiskClientError{Message: "unreachable"}
}

func (j *jsonHTTP) doOnce(ctx context.Context, method, url string, body []byte, result any) error {
	var reqBody io.Reader
	if body != nil {
		reqBody = bytes.NewReader(body)
	}
	req, err := http.NewRequestWithContext(ctx, method, url, reqBody)
	if err != nil {
		return &RiskClientError{Message: "build request", Cause: err}
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "application/json")
	req.Header.Set("X-API-Key", j.cfg.APIKey)

	resp, err := j.client.Do(req)
	if err != nil {
		return &RiskClientError{Message: fmt.Sprintf("%s %s", method, url), Cause: err}
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return &RiskClientError{StatusCode: resp.StatusCode, Message: fmt.Sprintf("%s %s", method, url)}
	}

	if result == nil {
		return nil
	}
	if err := json.NewDecoder(resp.Body).Decode(result); err != nil {
		// Tolerate empty or non-JSON body on 2xx responses (e.g. plain 200 from health checks).
		// io.EOF means empty body — that is not an error for us.
		if err.Error() == "EOF" {
			return nil
		}
		return err
	}
	return nil
}

// openSSEStream opens a long-lived SSE connection and returns the response body.
func (j *jsonHTTP) openSSEStream(ctx context.Context, url string) (io.ReadCloser, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Accept", "text/event-stream")
	req.Header.Set("Cache-Control", "no-cache")
	req.Header.Set("X-API-Key", j.cfg.APIKey)

	// SSE connections are long-lived; override the default short timeout.
	sseClient := &http.Client{}
	resp, err := sseClient.Do(req)
	if err != nil {
		return nil, &RiskClientError{Message: "SSE connect", Cause: err}
	}
	if resp.StatusCode != http.StatusOK {
		resp.Body.Close()
		return nil, &RiskClientError{StatusCode: resp.StatusCode, Message: "SSE stream"}
	}
	return resp.Body, nil
}

func isType[T any](err error, target *T) bool {
	v, ok := err.(T)
	if ok {
		*target = v
	}
	return ok
}
