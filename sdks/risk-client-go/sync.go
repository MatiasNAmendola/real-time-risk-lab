package riskclient

import "context"

// SyncClient provides synchronous REST operations.
type SyncClient struct {
	http    *jsonHTTP
	baseURL string
}

func newSyncClient(cfg Config, h *jsonHTTP) *SyncClient {
	return &SyncClient{http: h, baseURL: envMap[cfg.Environment].restBaseURL}
}

// Evaluate submits a risk request and returns the decision.
func (s *SyncClient) Evaluate(ctx context.Context, req RiskRequest) (*RiskDecision, error) {
	var dec RiskDecision
	if err := s.http.postJSON(ctx, s.baseURL+"/risk", req, &dec); err != nil {
		return nil, err
	}
	return &dec, nil
}

// EvaluateBatch evaluates multiple requests in one call.
func (s *SyncClient) EvaluateBatch(ctx context.Context, reqs []RiskRequest) ([]RiskDecision, error) {
	var decisions []RiskDecision
	if err := s.http.postJSON(ctx, s.baseURL+"/risk/batch", reqs, &decisions); err != nil {
		return nil, err
	}
	return decisions, nil
}

// Health checks engine liveness.
func (s *SyncClient) Health(ctx context.Context) (*HealthStatus, error) {
	var status HealthStatus
	if err := s.http.getJSON(ctx, s.baseURL+"/healthz", &status); err != nil {
		if fallbackErr := s.http.getJSON(ctx, s.baseURL+"/health", &status); fallbackErr != nil {
			return nil, err
		}
	}
	return &status, nil
}
