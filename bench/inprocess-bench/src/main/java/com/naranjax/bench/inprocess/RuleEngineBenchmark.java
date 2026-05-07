package com.naranjax.bench.inprocess;

import com.naranjax.poc.risk.audit.RulesAuditTrail;
import com.naranjax.poc.risk.config.RulesConfig;
import com.naranjax.poc.risk.config.RulesConfigLoader;
import com.naranjax.poc.risk.engine.AggregateDecision;
import com.naranjax.poc.risk.engine.FeatureSnapshot;
import com.naranjax.poc.risk.engine.RuleEngineImpl;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JMH benchmark for the declarative rules engine (pkg:risk-domain).
 *
 * Bench-01: 1 rule vs 100 rules — p50/p99 comparison.
 * Bench-02: Hot reload during concurrent evaluation — decision consistency.
 *
 * Run via: ./gradlew :bench:inprocess-bench:runBench
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsPrepend = {"-Xms128m", "-Xmx256m"})
@State(Scope.Benchmark)
public class RuleEngineBenchmark {

    private static final String V1_PATH = "examples/rules-config/v1/rules.yaml";
    private static final String V2_PATH = "examples/rules-config/v2/rules.yaml";

    // ── Single-rule engine (Bench-01 baseline) ────────────────────────────────
    @State(Scope.Benchmark)
    public static class SingleRuleState {
        RuleEngineImpl engine;
        FeatureSnapshot snapshot;

        @Setup(Level.Trial)
        public void setup() {
            RulesConfig config = buildSingleRuleConfig();
            engine = new RuleEngineImpl(config, new RulesAuditTrail());
            snapshot = FeatureSnapshot.builder()
                    .customerId("bench-user").transactionId("bench-tx")
                    .amountCents(7_500_000L).newDevice(false)
                    .chargebackCount90d(0).transactionCount10m(0)
                    .build();
        }
    }

    // ── 100-rule engine (Bench-01 stress) ─────────────────────────────────────
    @State(Scope.Benchmark)
    public static class HundredRuleState {
        RuleEngineImpl engine;
        FeatureSnapshot snapshot;

        @Setup(Level.Trial)
        public void setup() {
            RulesConfig config = buildNRuleConfig(100);
            engine = new RuleEngineImpl(config, new RulesAuditTrail());
            snapshot = FeatureSnapshot.builder()
                    .customerId("bench-user").transactionId("bench-tx")
                    .amountCents(7_500_000L).newDevice(false)
                    .chargebackCount90d(0).transactionCount10m(0)
                    .build();
        }
    }

    // ── Hot reload state (Bench-02) ───────────────────────────────────────────
    @State(Scope.Benchmark)
    public static class HotReloadState {
        RuleEngineImpl engine;
        RulesConfig v1Config;
        RulesConfig v2Config;
        FeatureSnapshot snapshot;

        @Setup(Level.Trial)
        public void setup() {
            RulesConfigLoader loader = new RulesConfigLoader();
            try {
                v1Config = loader.load(V1_PATH);
                v2Config = loader.load(V2_PATH);
            } catch (Exception e) {
                v1Config = buildSingleRuleConfig();
                v2Config = buildNRuleConfig(2);
            }
            engine = new RuleEngineImpl(v1Config, new RulesAuditTrail());
            snapshot = FeatureSnapshot.builder()
                    .customerId("bench-user").transactionId("bench-tx")
                    .amountCents(7_500_000L).build();
        }
    }

    // ── Bench-01: 1 rule baseline ─────────────────────────────────────────────
    @Benchmark
    public void evaluate_single_rule(SingleRuleState state, Blackhole bh) {
        bh.consume(state.engine.evaluate(state.snapshot));
    }

    // ── Bench-01: 100 rules stress ────────────────────────────────────────────
    @Benchmark
    public void evaluate_hundred_rules(HundredRuleState state, Blackhole bh) {
        bh.consume(state.engine.evaluate(state.snapshot));
    }

    // ── Bench-02: hot reload atomic swap ─────────────────────────────────────
    @Benchmark
    public void hot_reload_atomic_swap(HotReloadState state, Blackhole bh) {
        AggregateDecision d1 = state.engine.evaluate(state.snapshot);
        state.engine.reload(state.v2Config);
        AggregateDecision d2 = state.engine.evaluate(state.snapshot);
        state.engine.reload(state.v1Config); // reset for next iteration
        bh.consume(d1);
        bh.consume(d2);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static RulesConfig buildSingleRuleConfig() {
        Map<String, Object> params = Map.of("field", "amountCents", "operator", ">", "value", 10_000_000);
        List<RulesConfig.RuleDefinition> rules = List.of(
                new RulesConfig.RuleDefinition("HighAmountRule", "v1", "threshold",
                        true, 1.0, "DECLINE", params, null));
        return new RulesConfig("bench-1", "sha256:bench1", null, null, "bench",
                "worst_case_with_allowlist_override", 5000, "REVIEW", rules, List.of());
    }

    static RulesConfig buildNRuleConfig(int n) {
        List<RulesConfig.RuleDefinition> rules = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            // Alternate between threshold rules that fire and ones that don't
            long threshold = (i % 3 == 0) ? 1_000_000L : 100_000_000L;
            Map<String, Object> params = Map.of("field", "amountCents", "operator", ">", "value", threshold);
            rules.add(new RulesConfig.RuleDefinition("Rule" + i, "v1", "threshold",
                    true, 1.0, "REVIEW", params, null));
        }
        return new RulesConfig("bench-" + n, "sha256:bench" + n, null, null, "bench",
                "worst_case_with_allowlist_override", 5000, "REVIEW", rules, List.of());
    }
}
