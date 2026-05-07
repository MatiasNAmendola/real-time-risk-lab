package com.naranjax.poc.risk.rule;

import com.naranjax.poc.risk.engine.FeatureSnapshot;
import com.naranjax.poc.risk.rule.threshold.ThresholdRule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for ThresholdRule — covers all boundary cases from test plan doc 20 section 1.1-1.2.
 */
class ThresholdRuleTest {

    private static final long THRESHOLD = 10_000_000L;

    private ThresholdRule highAmountRule() {
        return new ThresholdRule("HighAmountRule", true, 1.0, RuleAction.DECLINE,
                "amountCents", ">", THRESHOLD);
    }

    // UT-THR-01: just above threshold
    @Test
    void evaluate_returns_triggered_when_amount_exceeds_threshold() {
        FeatureSnapshot snap = FeatureSnapshot.builder().amountCents(10_000_001L).build();
        RuleEvaluation result = highAmountRule().evaluate(snap);
        assertThat(result.triggered()).isTrue();
        assertThat(result.action()).isEqualTo(RuleAction.DECLINE);
    }

    // UT-THR-02: exactly equal (operator >, not >=)
    @Test
    void evaluate_returns_not_triggered_when_amount_equals_threshold_with_strict_greater() {
        FeatureSnapshot snap = FeatureSnapshot.builder().amountCents(THRESHOLD).build();
        RuleEvaluation result = highAmountRule().evaluate(snap);
        assertThat(result.triggered()).isFalse();
    }

    // UT-THR-03: just below threshold
    @Test
    void evaluate_returns_not_triggered_when_amount_below_threshold() {
        FeatureSnapshot snap = FeatureSnapshot.builder().amountCents(9_999_999L).build();
        RuleEvaluation result = highAmountRule().evaluate(snap);
        assertThat(result.triggered()).isFalse();
    }

    // UT-THR-04: zero amount
    @Test
    void evaluate_returns_not_triggered_when_amount_is_zero() {
        FeatureSnapshot snap = FeatureSnapshot.builder().amountCents(0L).build();
        assertThat(highAmountRule().evaluate(snap).triggered()).isFalse();
    }

    // UT-THR-05: negative amount
    @Test
    void evaluate_returns_not_triggered_when_amount_is_negative() {
        FeatureSnapshot snap = FeatureSnapshot.builder().amountCents(-1L).build();
        assertThat(highAmountRule().evaluate(snap).triggered()).isFalse();
    }

    // UT-THR-06: null field throws FieldNotFoundException with canonical message
    @Test
    void evaluate_throws_FieldNotFoundException_when_required_field_is_null() {
        FeatureSnapshot snap = FeatureSnapshot.builder().build(); // amountCents is null
        assertThatThrownBy(() -> highAmountRule().evaluate(snap))
                .isInstanceOf(FieldNotFoundException.class)
                .hasMessageContaining("Field 'amountCents' not found in snapshot");
    }

    // UT-THR-07: >= operator, exact match
    @Test
    void evaluate_returns_triggered_for_gte_operator_when_value_equals_threshold() {
        ThresholdRule rule = new ThresholdRule("r", true, 1.0, RuleAction.FLAG, "amountCents", ">=", 100);
        assertThat(rule.evaluate(FeatureSnapshot.builder().amountCents(100L).build()).triggered()).isTrue();
    }

    // UT-THR-08: >= operator, one below
    @Test
    void evaluate_returns_not_triggered_for_gte_operator_when_value_below_threshold() {
        ThresholdRule rule = new ThresholdRule("r", true, 1.0, RuleAction.FLAG, "amountCents", ">=", 100);
        assertThat(rule.evaluate(FeatureSnapshot.builder().amountCents(99L).build()).triggered()).isFalse();
    }

    // UT-THR-09: < operator, below threshold
    @Test
    void evaluate_returns_triggered_for_lt_operator_when_value_below_threshold() {
        ThresholdRule rule = new ThresholdRule("r", true, 1.0, RuleAction.REVIEW, "amountCents", "<", 100);
        assertThat(rule.evaluate(FeatureSnapshot.builder().amountCents(50L).build()).triggered()).isTrue();
    }

    // UT-THR-10: < operator, exactly at threshold (should NOT trigger)
    @Test
    void evaluate_returns_not_triggered_for_lt_operator_when_value_equals_threshold() {
        ThresholdRule rule = new ThresholdRule("r", true, 1.0, RuleAction.REVIEW, "amountCents", "<", 100);
        assertThat(rule.evaluate(FeatureSnapshot.builder().amountCents(100L).build()).triggered()).isFalse();
    }

    // UT-THR-11: == operator, exact match
    @Test
    void evaluate_returns_triggered_for_eq_operator_when_values_match() {
        ThresholdRule rule = new ThresholdRule("r", true, 1.0, RuleAction.FLAG, "amountCents", "==", 500);
        assertThat(rule.evaluate(FeatureSnapshot.builder().amountCents(500L).build()).triggered()).isTrue();
    }

    // UT-THR-12: == operator, no match
    @Test
    void evaluate_returns_not_triggered_for_eq_operator_when_values_differ() {
        ThresholdRule rule = new ThresholdRule("r", true, 1.0, RuleAction.FLAG, "amountCents", "==", 500);
        assertThat(rule.evaluate(FeatureSnapshot.builder().amountCents(501L).build()).triggered()).isFalse();
    }

    // UT-THR-13: != operator, same value (should NOT trigger)
    @Test
    void evaluate_returns_not_triggered_for_neq_operator_when_values_are_equal() {
        ThresholdRule rule = new ThresholdRule("r", true, 1.0, RuleAction.FLAG, "amountCents", "!=", 0);
        assertThat(rule.evaluate(FeatureSnapshot.builder().amountCents(0L).build()).triggered()).isFalse();
    }

    // UT-THR-14: != operator, different value
    @Test
    void evaluate_returns_triggered_for_neq_operator_when_values_differ() {
        ThresholdRule rule = new ThresholdRule("r", true, 1.0, RuleAction.FLAG, "amountCents", "!=", 0);
        assertThat(rule.evaluate(FeatureSnapshot.builder().amountCents(1L).build()).triggered()).isTrue();
    }

    // UT-THR-15: <= operator, exact match
    @Test
    void evaluate_returns_triggered_for_lte_operator_when_value_equals_threshold() {
        ThresholdRule rule = new ThresholdRule("r", true, 1.0, RuleAction.FLAG, "amountCents", "<=", 100);
        assertThat(rule.evaluate(FeatureSnapshot.builder().amountCents(100L).build()).triggered()).isTrue();
    }
}
