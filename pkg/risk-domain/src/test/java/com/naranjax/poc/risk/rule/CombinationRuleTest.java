package com.naranjax.poc.risk.rule;

import com.naranjax.poc.risk.engine.FeatureSnapshot;
import com.naranjax.poc.risk.rule.combination.CombinationRule;
import com.naranjax.poc.risk.rule.combination.SubRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for CombinationRule — test plan doc 20 section 1.3 (UT-COM-01 through UT-COM-08).
 */
class CombinationRuleTest {

    private CombinationRule newDeviceYoungCustomer(boolean requireAll) {
        return new CombinationRule("NewDeviceYoungCustomer", true, 0.7, RuleAction.REVIEW, requireAll, List.of(
                new SubRule.BooleanSubRule("newDevice", true),
                new SubRule.ThresholdSubRule("customerAgeDays", "<", 30)
        ));
    }

    // UT-COM-01: AND — both true
    @Test
    void evaluate_returns_triggered_when_all_subrules_match_with_requireAll_true() {
        FeatureSnapshot snap = FeatureSnapshot.builder().newDevice(true).customerAgeDays(20).build();
        assertThat(newDeviceYoungCustomer(true).evaluate(snap).triggered()).isTrue();
    }

    // UT-COM-02: AND — newDevice false
    @Test
    void evaluate_returns_not_triggered_when_newDevice_is_false_with_requireAll_true() {
        FeatureSnapshot snap = FeatureSnapshot.builder().newDevice(false).customerAgeDays(20).build();
        assertThat(newDeviceYoungCustomer(true).evaluate(snap).triggered()).isFalse();
    }

    // UT-COM-03: AND — customerAgeDays is exactly 30 (not < 30)
    @Test
    void evaluate_returns_not_triggered_when_customerAgeDays_equals_boundary_with_strict_lt() {
        FeatureSnapshot snap = FeatureSnapshot.builder().newDevice(true).customerAgeDays(30).build();
        assertThat(newDeviceYoungCustomer(true).evaluate(snap).triggered()).isFalse();
    }

    // UT-COM-04: AND — both false
    @Test
    void evaluate_returns_not_triggered_when_no_subrules_match_with_requireAll_true() {
        FeatureSnapshot snap = FeatureSnapshot.builder().newDevice(false).customerAgeDays(45).build();
        assertThat(newDeviceYoungCustomer(true).evaluate(snap).triggered()).isFalse();
    }

    // UT-COM-05: OR — only newDevice true
    @Test
    void evaluate_returns_triggered_when_at_least_one_subrule_matches_with_requireAll_false() {
        FeatureSnapshot snap = FeatureSnapshot.builder().newDevice(true).customerAgeDays(45).build();
        assertThat(newDeviceYoungCustomer(false).evaluate(snap).triggered()).isTrue();
    }

    // UT-COM-06: OR — only customerAgeDays triggers
    @Test
    void evaluate_returns_triggered_when_only_threshold_subrule_matches_with_requireAll_false() {
        FeatureSnapshot snap = FeatureSnapshot.builder().newDevice(false).customerAgeDays(20).build();
        assertThat(newDeviceYoungCustomer(false).evaluate(snap).triggered()).isTrue();
    }

    // UT-COM-07: OR — neither matches
    @Test
    void evaluate_returns_not_triggered_when_no_subrules_match_with_requireAll_false() {
        FeatureSnapshot snap = FeatureSnapshot.builder().newDevice(false).customerAgeDays(45).build();
        assertThat(newDeviceYoungCustomer(false).evaluate(snap).triggered()).isFalse();
    }

    // UT-COM-08: empty subrules list never triggers
    @Test
    void evaluate_returns_not_triggered_when_subrules_list_is_empty() {
        CombinationRule emptyRule = new CombinationRule("EmptyRule", true, 1.0,
                RuleAction.REVIEW, true, List.of());
        FeatureSnapshot snap = FeatureSnapshot.builder().newDevice(true).customerAgeDays(10).build();
        assertThat(emptyRule.evaluate(snap).triggered()).isFalse();
    }
}
