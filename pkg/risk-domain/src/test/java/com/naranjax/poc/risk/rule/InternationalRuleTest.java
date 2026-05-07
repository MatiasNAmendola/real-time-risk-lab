package com.naranjax.poc.risk.rule;

import com.naranjax.poc.risk.engine.FeatureSnapshot;
import com.naranjax.poc.risk.rule.international.InternationalRule;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for InternationalRule — test plan doc 20 section 1.6 (UT-INT-01 through UT-INT-03).
 */
class InternationalRuleTest {

    private InternationalRule rule() {
        return new InternationalRule("InternationalRestricted", true, 0.5, RuleAction.REVIEW,
                Set.of("NK", "IR", "SY"));
    }

    // UT-INT-01: restricted country
    @Test
    void evaluate_returns_triggered_when_country_is_in_restricted_list() {
        FeatureSnapshot snap = FeatureSnapshot.builder().country("NK").build();
        assertThat(rule().evaluate(snap).triggered()).isTrue();
    }

    // UT-INT-02: allowed country
    @Test
    void evaluate_returns_not_triggered_when_country_is_not_restricted() {
        FeatureSnapshot snap = FeatureSnapshot.builder().country("AR").build();
        assertThat(rule().evaluate(snap).triggered()).isFalse();
    }

    // UT-INT-03: null country (domestic transaction)
    @Test
    void evaluate_returns_not_triggered_when_country_is_null() {
        FeatureSnapshot snap = FeatureSnapshot.builder().build(); // country = null
        assertThat(rule().evaluate(snap).triggered()).isFalse();
    }
}
