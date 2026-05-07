package com.naranjax.distributed.shared;

/**
 * Canonical event bus addresses shared across all JVM processes.
 * Changing these strings requires redeploying ALL three apps.
 */
public final class EventBusAddress {

    private EventBusAddress() {}

    /** controller-app → usecase-app: evaluate a risk request */
    public static final String USECASE_EVALUATE = "usecase.evaluate";

    /** usecase-app → repository-app: fetch customer feature snapshot */
    public static final String REPOSITORY_FIND_FEATURES = "repository.findFeatures";

    /** usecase-app publish/subscribe → all controller-app instances (SSE + WS broadcast) */
    public static final String RISK_DECISION_BROADCAST = "risk.decision.broadcast";

    /** usecase-app → repository-app: look up a stored decision by idempotency key */
    public static final String REPOSITORY_IDEMPOTENCY_GET = "repository.idempotency.get";

    /** usecase-app → repository-app: store a new decision under an idempotency key */
    public static final String REPOSITORY_IDEMPOTENCY_PUT = "repository.idempotency.put";
}
