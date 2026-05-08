package io.riskplatform.monolith.observability;

import io.opentelemetry.api.trace.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility methods for OTel span attribute enrichment.
 *
 * <p>The OTel Java agent is attached via the JVM -javaagent flag.
 * Custom span attributes are set via the OpenTelemetry API.
 */
public final class OtelHelper {

    private static final Logger log = LoggerFactory.getLogger(OtelHelper.class);

    private OtelHelper() {}

    /** Enriches the current span with risk decision attributes. */
    public static void enrichDecisionSpan(String decision, double mlScore,
                                          long budgetRemainingMs, boolean fallback,
                                          long amountCents, String correlationId,
                                          String circuitBreakerState) {
        try {
            Span span = Span.current();
            if (!span.getSpanContext().isValid()) return;
            span.setAttribute("risk.decision",           decision);
            span.setAttribute("risk.ml.score",           mlScore);
            span.setAttribute("risk.budget.remaining_ms", budgetRemainingMs);
            span.setAttribute("risk.fallback_applied",   fallback);
            span.setAttribute("risk.amount_cents",       amountCents);
            span.setAttribute("risk.correlation_id",     correlationId);
            span.setAttribute("risk.cb.state",           circuitBreakerState);
        } catch (Exception e) {
            log.debug("[monolith] OtelHelper: span attribute error (non-fatal): {}", e.getMessage());
        }
    }

    /** Adds a rule-level attribute to the current span. */
    public static void addRuleAttribute(String ruleName, boolean triggered) {
        try {
            Span.current().setAttribute("risk.rule." + ruleName, triggered);
        } catch (Exception ignored) {}
    }
}
