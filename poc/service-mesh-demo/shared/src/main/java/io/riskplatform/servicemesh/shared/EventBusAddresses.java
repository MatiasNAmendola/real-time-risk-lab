package io.riskplatform.servicemesh.shared;

/** EventBus API contracts between independently-owned bounded contexts. */
public final class EventBusAddresses {
    private EventBusAddresses() {}

    public static final String FRAUD_RULES_EVALUATE = "fraud-rules.evaluate.v1";
    public static final String ML_SCORER_SCORE = "ml-scorer.score.v1";
    public static final String AUDIT_RECORD = "audit.record.v1";
}
