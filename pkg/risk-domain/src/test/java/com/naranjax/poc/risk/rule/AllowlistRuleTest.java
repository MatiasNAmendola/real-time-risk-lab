package com.naranjax.poc.risk.rule;

import com.naranjax.poc.risk.engine.FeatureSnapshot;
import com.naranjax.poc.risk.rule.allowlist.AllowlistRule;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for AllowlistRule — test plan doc 20 section 1.9 (UT-ALL-01 through UT-ALL-03).
 */
class AllowlistRuleTest {

    private AllowlistRule rule() {
        return new AllowlistRule("TrustedCustomerAllowlist", true, 999.0, true,
                Set.of("cust_vip_001", "cust_vip_002"));
    }

    // UT-ALL-01: customer in list
    @Test
    void evaluate_returns_triggered_when_customer_is_in_allowlist() {
        FeatureSnapshot snap = FeatureSnapshot.builder().customerId("cust_vip_001").build();
        RuleEvaluation result = rule().evaluate(snap);
        assertThat(result.triggered()).isTrue();
        assertThat(result.action()).isEqualTo(RuleAction.ALLOW);
    }

    // UT-ALL-02: unknown customer
    @Test
    void evaluate_returns_not_triggered_when_customer_is_not_in_allowlist() {
        FeatureSnapshot snap = FeatureSnapshot.builder().customerId("cust_unknown").build();
        assertThat(rule().evaluate(snap).triggered()).isFalse();
    }

    // UT-ALL-03: null customer
    @Test
    void evaluate_returns_not_triggered_when_customer_id_is_null() {
        FeatureSnapshot snap = FeatureSnapshot.builder().build();
        assertThat(rule().evaluate(snap).triggered()).isFalse();
    }
}
