package io.riskplatform.monolith.unit;

import io.riskplatform.monolith.usecase.MonolithEventBusAddress;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ensures MonolithEventBusAddress constants are stable and non-blank.
 * Changing these would break in-process verticle communication.
 */
class MonolithEventBusAddressTest {

    @Test
    void usecaseEvaluate_isNonBlank() {
        assertThat(MonolithEventBusAddress.USECASE_EVALUATE).isNotBlank();
    }

    @Test
    void riskDecisionBroadcast_isNonBlank() {
        assertThat(MonolithEventBusAddress.RISK_DECISION_BROADCAST).isNotBlank();
    }

    @Test
    void usecaseEvaluate_hasExpectedValue() {
        assertThat(MonolithEventBusAddress.USECASE_EVALUATE)
            .isEqualTo("monolith.usecase.evaluate");
    }

    @Test
    void riskDecisionBroadcast_hasExpectedValue() {
        assertThat(MonolithEventBusAddress.RISK_DECISION_BROADCAST)
            .isEqualTo("monolith.risk.decision.broadcast");
    }

    @Test
    void addresses_areDistinct() {
        assertThat(MonolithEventBusAddress.USECASE_EVALUATE)
            .isNotEqualTo(MonolithEventBusAddress.RISK_DECISION_BROADCAST);
    }
}
