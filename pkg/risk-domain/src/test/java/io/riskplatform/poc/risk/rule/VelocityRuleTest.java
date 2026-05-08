package io.riskplatform.rules.rule;

import io.riskplatform.rules.engine.FeatureSnapshot;
import io.riskplatform.rules.rule.velocity.VelocityRule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for VelocityRule — test plan doc 20 section 1.4 (UT-VEL-01 through UT-VEL-04).
 */
class VelocityRuleTest {

    private VelocityRule velocityRule() {
        return new VelocityRule("VelocityHigh", true, 0.8, RuleAction.REVIEW,
                5, 10, "customerId");
    }

    // UT-VEL-01: below threshold
    @Test
    void evaluate_returns_not_triggered_when_count_is_below_threshold() {
        FeatureSnapshot snap = FeatureSnapshot.builder().transactionCount10m(4).build();
        assertThat(velocityRule().evaluate(snap).triggered()).isFalse();
    }

    // UT-VEL-02: exactly at threshold
    @Test
    void evaluate_returns_triggered_when_count_equals_threshold() {
        FeatureSnapshot snap = FeatureSnapshot.builder().transactionCount10m(5).build();
        assertThat(velocityRule().evaluate(snap).triggered()).isTrue();
    }

    // UT-VEL-03: above threshold
    @Test
    void evaluate_returns_triggered_when_count_exceeds_threshold() {
        FeatureSnapshot snap = FeatureSnapshot.builder().transactionCount10m(6).build();
        assertThat(velocityRule().evaluate(snap).triggered()).isTrue();
    }

    // UT-VEL-04: zero count (expired window / no history)
    @Test
    void evaluate_returns_not_triggered_when_count_is_zero() {
        FeatureSnapshot snap = FeatureSnapshot.builder().transactionCount10m(0).build();
        assertThat(velocityRule().evaluate(snap).triggered()).isFalse();
    }
}
