package com.naranjax.poc.risk.engine;

import com.naranjax.poc.risk.config.RulesConfig;

/**
 * Main entry point for evaluating a FeatureSnapshot against the active rule configuration.
 *
 * Implementations must be thread-safe. Multiple threads may call evaluate() concurrently.
 * Hot reload must not produce an inconsistent view within a single evaluate() call.
 */
public interface RuleEngine {

    /**
     * Evaluates the snapshot against all enabled rules in the currently active configuration.
     *
     * @param snapshot pre-materialised feature signals for this transaction
     * @return aggregated decision with per-rule breakdown and config metadata
     */
    AggregateDecision evaluate(FeatureSnapshot snapshot);

    /**
     * Atomically replaces the active configuration.
     * In-flight evaluations that started before this call complete with the previous config.
     *
     * @param newConfig validated, loaded config (must have passed RulesConfigValidator)
     */
    void reload(RulesConfig newConfig);

    /** Returns the hash of the currently active configuration. */
    String activeConfigHash();

    /** Returns the currently active RulesConfig. */
    RulesConfig activeConfig();
}
