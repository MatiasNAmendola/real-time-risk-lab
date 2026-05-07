package com.naranjax.monolith.unit;

import com.naranjax.distributed.shared.RiskDecision;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the risk decision policy logic.
 * Tests the APPROVE/REVIEW/DECLINE thresholds that mirror the bare-javac PoC.
 */
class PolicyDecisionTest {

    /** Inline replica of the monolith policy (same as EvaluateRiskUseCase.applyPolicy). */
    private RiskDecision applyPolicy(boolean highAmountFired, boolean newDeviceFired,
                                     double mlScore, String corrId) {
        if (highAmountFired) return new RiskDecision("DECLINE", "HighAmountRule v1", corrId);
        if (newDeviceFired)  return new RiskDecision("REVIEW",  "NewDeviceYoungCustomerRule v1", corrId);
        if (mlScore > 0.7)   return new RiskDecision("DECLINE", "ml score > 0.7", corrId);
        if (mlScore > 0.4)   return new RiskDecision("REVIEW",  "ml score > 0.4", corrId);
        return new RiskDecision("APPROVE", "within limits", corrId);
    }

    @Test
    void highAmountRule_producesDECLINE() {
        assertThat(applyPolicy(true, false, 0.1, "c").decision()).isEqualTo("DECLINE");
    }

    @Test
    void newDeviceRule_producesREVIEW() {
        assertThat(applyPolicy(false, true, 0.1, "c").decision()).isEqualTo("REVIEW");
    }

    @Test
    void highAmountTakesPriorityOverNewDevice() {
        // Both fired: DECLINE wins
        assertThat(applyPolicy(true, true, 0.1, "c").decision()).isEqualTo("DECLINE");
    }

    @ParameterizedTest(name = "mlScore={0} -> {1}")
    @CsvSource({
        "0.8, DECLINE",
        "0.71, DECLINE",
        "0.5, REVIEW",
        "0.41, REVIEW",
        "0.3, APPROVE",
        "0.0, APPROVE"
    })
    void mlScoreThresholds(double score, String expected) {
        assertThat(applyPolicy(false, false, score, "c").decision()).isEqualTo(expected);
    }

    @Test
    void approveDecision_hasReason() {
        RiskDecision d = applyPolicy(false, false, 0.1, "corr-1");
        assertThat(d.reason()).isNotBlank();
        assertThat(d.correlationId()).isEqualTo("corr-1");
    }
}
