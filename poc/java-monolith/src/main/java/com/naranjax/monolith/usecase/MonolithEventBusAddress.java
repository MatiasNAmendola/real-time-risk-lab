package com.naranjax.monolith.usecase;

/**
 * Local event bus addresses for the single-JVM monolith.
 *
 * <p>These addresses are used for in-process communication between verticles
 * in the same Vert.x instance. No Hazelcast clustering is required.
 *
 * <p>Contrast with java-vertx-distributed where the same addresses cross
 * JVM boundaries via Hazelcast cluster event bus.
 */
public final class MonolithEventBusAddress {

    private MonolithEventBusAddress() {}

    /** controller verticle -> usecase verticle: evaluate a risk transaction */
    public static final String USECASE_EVALUATE = "monolith.usecase.evaluate";

    /** usecase verticle -> controller verticle: broadcast decision for SSE + WS fanout */
    public static final String RISK_DECISION_BROADCAST = "monolith.risk.decision.broadcast";
}
