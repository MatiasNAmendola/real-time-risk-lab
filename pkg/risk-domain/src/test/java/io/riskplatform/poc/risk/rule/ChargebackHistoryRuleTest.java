package io.riskplatform.rules.rule;

import io.riskplatform.rules.engine.FeatureSnapshot;
import io.riskplatform.rules.rule.chargeback.ChargebackHistoryRule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ChargebackHistoryRule — test plan doc 20 section 1.5 (UT-CHB-01 through UT-CHB-03).
 */
class ChargebackHistoryRuleTest {

    private ChargebackHistoryRule rule() {
        return new ChargebackHistoryRule("ChargebackHistory", true, 0.9, RuleAction.DECLINE,
                "chargebackCount90d", ">=", 1);
    }

    // UT-CHB-01: zero chargebacks — should not trigger
    @Test
    void evaluate_returns_not_triggered_when_chargeback_count_is_zero() {
        FeatureSnapshot snap = FeatureSnapshot.builder().chargebackCount90d(0).build();
        assertThat(rule().evaluate(snap).triggered()).isFalse();
    }

    // UT-CHB-02: exactly 1 chargeback — should trigger
    @Test
    void evaluate_returns_triggered_when_chargeback_count_equals_threshold() {
        FeatureSnapshot snap = FeatureSnapshot.builder().chargebackCount90d(1).build();
        assertThat(rule().evaluate(snap).triggered()).isTrue();
    }

    // UT-CHB-03: multiple chargebacks — should trigger
    @Test
    void evaluate_returns_triggered_when_chargeback_count_exceeds_threshold() {
        FeatureSnapshot snap = FeatureSnapshot.builder().chargebackCount90d(3).build();
        RuleEvaluation result = rule().evaluate(snap);
        assertThat(result.triggered()).isTrue();
        assertThat(result.action()).isEqualTo(RuleAction.DECLINE);
    }
}
