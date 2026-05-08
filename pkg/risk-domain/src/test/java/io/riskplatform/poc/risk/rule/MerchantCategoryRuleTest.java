package io.riskplatform.rules.rule;

import io.riskplatform.rules.engine.FeatureSnapshot;
import io.riskplatform.rules.rule.mcc.MerchantCategoryRule;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for MerchantCategoryRule — test plan doc 20 section 1.8 (UT-MCC-01 through UT-MCC-03).
 */
class MerchantCategoryRuleTest {

    private MerchantCategoryRule rule() {
        return new MerchantCategoryRule("HighRiskMerchant", true, 0.6, RuleAction.REVIEW,
                Set.of("7995", "5993", "5816"));
    }

    // UT-MCC-01: gambling MCC
    @Test
    void evaluate_returns_triggered_when_mcc_is_in_high_risk_list() {
        FeatureSnapshot snap = FeatureSnapshot.builder().merchantMcc("7995").build();
        assertThat(rule().evaluate(snap).triggered()).isTrue();
    }

    // UT-MCC-02: grocery MCC — not in list
    @Test
    void evaluate_returns_not_triggered_when_mcc_is_not_in_list() {
        FeatureSnapshot snap = FeatureSnapshot.builder().merchantMcc("5411").build();
        assertThat(rule().evaluate(snap).triggered()).isFalse();
    }

    // UT-MCC-03: null MCC
    @Test
    void evaluate_returns_not_triggered_when_mcc_is_null() {
        FeatureSnapshot snap = FeatureSnapshot.builder().build();
        assertThat(rule().evaluate(snap).triggered()).isFalse();
    }
}
