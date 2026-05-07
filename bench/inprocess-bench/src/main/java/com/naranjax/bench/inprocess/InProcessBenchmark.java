package com.naranjax.bench.inprocess;

import com.naranjax.interview.risk.domain.context.ExecutionContext;
import com.naranjax.interview.risk.config.RiskApplicationFactory;
import com.naranjax.interview.risk.domain.entity.*;
import com.naranjax.interview.risk.domain.usecase.EvaluateRiskUseCase;
import com.naranjax.interview.risk.infrastructure.repository.log.ConsoleStructuredLogger;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.time.Duration;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark for the bare-javac in-process risk engine.
 *
 * Measures EvaluateRiskUseCase.evaluate() directly — no HTTP overhead.
 * Three modes: throughput, average time, sample time (for p99).
 *
 * Run via: java -jar target/inprocess-bench.jar
 * Or:      mvn package && java -jar target/inprocess-bench.jar -rf json -rff ../../out/perf/inprocess.json
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsPrepend = {"-Xms256m", "-Xmx512m"})
@State(Scope.Benchmark)
public class InProcessBenchmark {

    private RiskApplicationFactory factory;
    private EvaluateRiskUseCase useCase;

    // Pre-built request pool: avoid UUID allocation overhead in the hot path.
    // Pool size > concurrency avoids contention on the same idempotency key.
    private static final int POOL_SIZE = 1024;
    private TransactionRiskRequest[] requestPool;
    private int poolIndex = 0;

    private static final long[] AMOUNTS_CENTS = {
        1_000L, 5_000L, 15_000L, 50_000L, 150_000L, 200_000L, 500_000L
    };

    @Setup(Level.Trial)
    public void setup() {
        factory = new RiskApplicationFactory(/*silent=*/true);
        useCase = factory.evaluateRiskUseCase();
        requestPool = new TransactionRiskRequest[POOL_SIZE];
        var rng = new Random(42);
        for (int i = 0; i < POOL_SIZE; i++) {
            long amount = AMOUNTS_CENTS[rng.nextInt(AMOUNTS_CENTS.length)];
            boolean newDevice = (rng.nextInt(7) == 0);
            requestPool[i] = new TransactionRiskRequest(
                new TransactionId("tx-jmh-" + i),
                new CustomerId("user-" + (i % 1000)),
                new Money(amount, "ARS"),
                newDevice,
                new CorrelationId(UUID.randomUUID().toString()),
                // Unique idempotency key per slot — prevents cache short-circuit.
                new IdempotencyKey("bench-" + UUID.randomUUID())
            );
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() throws Exception {
        factory.close();
    }

    /**
     * Hot benchmark: single evaluate call using a rotating request from the pool.
     * Each invocation draws from the pool round-robin to spread across decisions.
     */
    @Benchmark
    public void evaluateRisk(Blackhole bh) {
        var request = requestPool[(poolIndex++) & (POOL_SIZE - 1)];
        var context = new ExecutionContext(
            request.correlationId(),
            new ConsoleStructuredLogger(/*silent=*/true)
        );
        var result = useCase.evaluate(context, request, Duration.ofMillis(300));
        bh.consume(result);
    }

    /**
     * Convenience main for running outside Maven (IDE, CI).
     * Writes results to ../../out/perf/ relative to CWD.
     */
    public static void main(String[] args) throws Exception {
        String outDir = args.length > 0 ? args[0] : "out/perf";
        var opts = new OptionsBuilder()
            .include(InProcessBenchmark.class.getSimpleName())
            .warmupIterations(2)
            .measurementIterations(5)
            .forks(1)
            .resultFormat(ResultFormatType.JSON)
            .result(outDir + "/inprocess-jmh-" + System.currentTimeMillis() + ".json")
            .build();
        new Runner(opts).run();
    }
}
