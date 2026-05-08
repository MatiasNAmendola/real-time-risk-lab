package io.riskplatform.monolith.unit;

import io.riskplatform.monolith.observability.OtelHelper;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for OtelHelper.
 * OtelHelper is non-fatal: it must never throw even with invalid span context.
 */
class OtelHelperTest {

    @Test
    void enrichDecisionSpan_doesNotThrow_withInvalidContext() {
        // No OTel agent in unit test context — span.getSpanContext().isValid() == false
        OtelHelper.enrichDecisionSpan("APPROVE", 0.3, 200, false, 10000L, "corr-1", "CLOSED");
    }

    @Test
    void addRuleAttribute_doesNotThrow_withInvalidContext() {
        OtelHelper.addRuleAttribute("HighAmountRule", true);
    }

    @Test
    void enrichDecisionSpan_acceptsAllDecisionValues() {
        OtelHelper.enrichDecisionSpan("DECLINE", 0.9, 50, true,  200000L, "corr-2", "OPEN");
        OtelHelper.enrichDecisionSpan("REVIEW",  0.5, 100, false, 50000L, "corr-3", "HALF_OPEN");
    }
}
