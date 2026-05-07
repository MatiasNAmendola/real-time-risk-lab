package riskclient

import "context"

// AdminClient provides rule management and audit operations.
type AdminClient struct {
	http    *jsonHTTP
	baseURL string
}

func newAdminClient(cfg Config, h *jsonHTTP) *AdminClient {
	return &AdminClient{http: h, baseURL: envMap[cfg.Environment].restBaseURL}
}

// ListRules returns all currently active rules.
func (a *AdminClient) ListRules(ctx context.Context) ([]RuleInfo, error) {
	var rules []RuleInfo
	if err := a.http.getJSON(ctx, a.baseURL+"/admin/rules", &rules); err != nil {
		return nil, err
	}
	return rules, nil
}

// ReloadRules triggers a hot reload of the rule store.
func (a *AdminClient) ReloadRules(ctx context.Context) error {
	return a.http.postJSON(ctx, a.baseURL+"/admin/rules/reload", map[string]any{}, nil)
}

// TestRule evaluates req without persisting the decision.
func (a *AdminClient) TestRule(ctx context.Context, req RiskRequest) (*RiskDecision, error) {
	var dec RiskDecision
	if err := a.http.postJSON(ctx, a.baseURL+"/admin/rules/test", req, &dec); err != nil {
		return nil, err
	}
	return &dec, nil
}

// RulesAuditTrail returns the immutable audit log for rule changes.
func (a *AdminClient) RulesAuditTrail(ctx context.Context) ([]map[string]any, error) {
	var entries []map[string]any
	if err := a.http.getJSON(ctx, a.baseURL+"/admin/rules/audit", &entries); err != nil {
		return nil, err
	}
	return entries, nil
}
