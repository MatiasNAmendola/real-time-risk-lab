package com.naranjax.distributed.shared.rules;

/**
 * Result of evaluating a single {@link FraudRule} against a request and its features.
 *
 * @param triggered {@code true} if the rule fired.
 * @param reason    human-readable explanation, e.g. "amount > 10000000" or "ok".
 * @param weight    influence weight of this rule in the final decision (0.0 – 1.0).
 */
public record RuleEvaluation(boolean triggered, String reason, double weight) {}
