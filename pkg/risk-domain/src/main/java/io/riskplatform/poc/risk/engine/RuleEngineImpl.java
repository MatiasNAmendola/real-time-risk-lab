package io.riskplatform.rules.engine;

import io.riskplatform.rules.audit.RulesAuditTrail;
import io.riskplatform.rules.config.RulesConfig;
import io.riskplatform.rules.rule.FraudRule;
import io.riskplatform.rules.rule.RuleAction;
import io.riskplatform.rules.rule.RuleEvaluation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe RuleEngine implementation using an AtomicReference for hot reload.
 *
 * Hot reload is atomic: evaluate() reads the reference once at the start of evaluation
 * and uses that snapshot throughout. A concurrent reload() call does not affect in-flight
 * evaluations. The invariant "no request sees a mixed version" is guaranteed.
 *
 * Timeout enforcement: if evaluation exceeds config.timeoutMs(), the engine returns the
 * fallback_decision immediately with timedOut=true.
 */
public final class RuleEngineImpl implements RuleEngine {

    private final AtomicReference<CompiledRuleSet> current;
    private final AggregationPolicy aggregationPolicy;
    private final RulesAuditTrail auditTrail;

    public RuleEngineImpl(RulesConfig initialConfig, RulesAuditTrail auditTrail) {
        this.current           = new AtomicReference<>(CompiledRuleSet.compile(initialConfig));
        this.aggregationPolicy = new AggregationPolicy();
        this.auditTrail        = auditTrail;
    }

    @Override
    public AggregateDecision evaluate(FeatureSnapshot snapshot) {
        // Read the current compiled rule set atomically — this reference is stable for the
        // duration of this evaluation, even if reload() is called concurrently.
        CompiledRuleSet ruleSet = current.get();
        RulesConfig config = ruleSet.config();

        long startMs = System.currentTimeMillis();
        long timeoutMs = config.timeoutMs() > 0 ? config.timeoutMs() : Long.MAX_VALUE;

        List<FraudRule> rules = ruleSet.rules();
        List<FraudRule> evaluatedRules = new ArrayList<>(rules.size());
        List<RuleEvaluation> results = new ArrayList<>(rules.size());

        // Evaluate each enabled rule
        for (FraudRule rule : rules) {
            if (!rule.enabled()) continue;
            evaluatedRules.add(rule);

            // Timeout check before each rule (avoids calling a slow rule after budget exhausted)
            if (System.currentTimeMillis() - startMs > timeoutMs) {
                RuleAction fallback = parseAction(config.fallbackDecision());
                long evalMs = System.currentTimeMillis() - startMs;
                AggregateDecision timedOut = new AggregateDecision(
                        fallback, List.copyOf(results),
                        config.hash(), config.version(),
                        true, null, evalMs);
                auditTrail.record(snapshot, timedOut);
                return timedOut;
            }

            try {
                results.add(rule.evaluate(snapshot));
            } catch (Exception e) {
                // Rule evaluation error is non-fatal — record as not-triggered
                results.add(RuleEvaluation.notTriggered(rule.name(),
                        "evaluation error: " + e.getMessage(), rule.enabled() ? 0 : 0,
                        RuleAction.APPROVE));
            }
        }

        AtomicReference<String> overriddenBy = new AtomicReference<>(null);
        RuleAction decision = aggregationPolicy.aggregate(results, evaluatedRules, overriddenBy);

        long evalMs = System.currentTimeMillis() - startMs;
        AggregateDecision result = new AggregateDecision(
                decision, List.copyOf(results),
                config.hash(), config.version(),
                false, overriddenBy.get(), evalMs);

        auditTrail.record(snapshot, result);
        return result;
    }

    @Override
    public void reload(RulesConfig newConfig) {
        CompiledRuleSet compiled = CompiledRuleSet.compile(newConfig);
        current.set(compiled);
    }

    @Override
    public String activeConfigHash() {
        return current.get().config().hash();
    }

    @Override
    public RulesConfig activeConfig() {
        return current.get().config();
    }

    private static RuleAction parseAction(String action) {
        if (action == null) return RuleAction.REVIEW;
        try {
            return RuleAction.valueOf(action.toUpperCase());
        } catch (IllegalArgumentException e) {
            return RuleAction.REVIEW;
        }
    }
}
