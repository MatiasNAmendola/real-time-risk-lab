package com.naranjax.interview.risk.cmd;

import com.naranjax.interview.risk.domain.context.ExecutionContext;
import com.naranjax.interview.risk.config.RiskApplicationFactory;
import com.naranjax.interview.risk.domain.entity.*;
import com.naranjax.interview.risk.domain.usecase.EvaluateRiskUseCase;
import com.naranjax.interview.risk.infrastructure.repository.log.ConsoleStructuredLogger;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Latency benchmark for the risk decision engine.
 * Inbound adapter — CLI controller. Equivalente a infrastructure/controllers/ (enterprise Go layout).
 *
 * <p>Usage: {@code BenchmarkRunner [N] [M] [warmup]}
 * <ul>
 *   <li>N      — number of measured requests (default 5000)</li>
 *   <li>M      — virtual-thread concurrency (default 32)</li>
 *   <li>warmup — warm-up iterations before measurement (default 500)</li>
 * </ul>
 */
public final class BenchmarkRunner {

    public static void main(String[] args) throws Exception {
        int N      = args.length > 0 ? Integer.parseInt(args[0]) : 5_000;
        int M      = args.length > 1 ? Integer.parseInt(args[1]) : 32;
        int warmup = args.length > 2 ? Integer.parseInt(args[2]) : 500;

        System.out.printf("=== Risk Engine Benchmark ===%n");
        System.out.printf("requests=%d  concurrency=%d  warmup=%d%n%n", N, M, warmup);

        try (var app = new RiskApplicationFactory(/*silent=*/true)) {
            var useCase = app.evaluateRiskUseCase();

            // ── Warm-up ────────────────────────────────────────────────────────
            System.out.print("Warming up... ");
            runBatch(useCase, warmup, M);
            System.out.println("done.");

            // ── Measured run ───────────────────────────────────────────────────
            System.out.println("Measuring...");
            var result = runBatch(useCase, N, M);

            // ── Report ─────────────────────────────────────────────────────────
            printReport(result, N);
        }
    }

    // ─── Batch execution ────────────────────────────────────────────────────────

    private record BatchResult(
            long[] latenciesNs,          // sorted after run
            long totalNs,
            Map<Decision, Long> decisionCounts,
            long fallbackCount
    ) {}

    private static BatchResult runBatch(
            EvaluateRiskUseCase useCase,
            int n,
            int concurrency
    ) throws Exception {

        long[] latencies = new long[n];
        var decisionCounts = new EnumMap<Decision, AtomicLong>(Decision.class);
        for (var d : Decision.values()) decisionCounts.put(d, new AtomicLong());
        var fallbackCount = new AtomicLong();
        var semaphore = new Semaphore(concurrency);
        var latch = new CountDownLatch(n);

        long wallStart = System.nanoTime();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < n; i++) {
                final int idx = i;
                semaphore.acquire();
                executor.submit(() -> {
                    try {
                        var request = makeRequest(idx);
                        var context = new ExecutionContext(
                                request.correlationId(),
                                new ConsoleStructuredLogger(/*silent=*/true)
                        );
                        long t0 = System.nanoTime();
                        var decision = useCase.evaluate(context, request, Duration.ofMillis(300));
                        latencies[idx] = System.nanoTime() - t0;
                        decisionCounts.get(decision.decision()).incrementAndGet();
                        if (!decision.trace().fallbacks().isEmpty()) fallbackCount.incrementAndGet();
                    } finally {
                        semaphore.release();
                        latch.countDown();
                    }
                });
            }
            latch.await();
        }

        long totalNs = System.nanoTime() - wallStart;
        Arrays.sort(latencies);

        // Materialize counts into plain map
        var counts = new EnumMap<Decision, Long>(Decision.class);
        for (var entry : decisionCounts.entrySet()) counts.put(entry.getKey(), entry.getValue().get());

        return new BatchResult(latencies, totalNs, counts, fallbackCount.get());
    }

    // ─── Request factory ────────────────────────────────────────────────────────

    private static final long[] AMOUNTS_CENTS = {
            500L, 1_200L, 5_000L, 8_500L, 15_000L, 50_000L, 70_000L, 120_000L
    };

    private static TransactionRiskRequest makeRequest(int idx) {
        var rng = new Random(idx);
        long amount = AMOUNTS_CENTS[rng.nextInt(AMOUNTS_CENTS.length)];
        boolean newDevice = (idx % 7 == 0);   // ~14 % new-device

        return new TransactionRiskRequest(
                new TransactionId("tx-bench-" + idx + "-" + UUID.randomUUID()),
                new CustomerId("user-" + (idx % 1000)),
                new Money(amount, "ARS"),
                newDevice,
                new CorrelationId(UUID.randomUUID().toString()),
                new IdempotencyKey("bench-idem-" + idx + "-" + UUID.randomUUID())  // always unique → no idempotency cache hits
        );
    }

    // ─── Report printer ─────────────────────────────────────────────────────────

    private static void printReport(BatchResult r, int n) {
        double totalMs   = r.totalNs() / 1_000_000.0;
        double throughput = n / (r.totalNs() / 1_000_000_000.0);

        System.out.println();
        System.out.println("─────────────────────────────────────────────");
        System.out.printf("  Total wall time  : %,.1f ms%n",  totalMs);
        System.out.printf("  Throughput       : %,.0f req/s%n", throughput);
        System.out.println("─────────────────────────────────────────────");
        System.out.printf("  p50              : %s%n", fmtNs(percentile(r.latenciesNs(), 50)));
        System.out.printf("  p95              : %s%n", fmtNs(percentile(r.latenciesNs(), 95)));
        System.out.printf("  p99              : %s%n", fmtNs(percentile(r.latenciesNs(), 99)));
        System.out.printf("  p99.9            : %s%n", fmtNs(percentile(r.latenciesNs(), 99.9)));
        System.out.printf("  max              : %s%n", fmtNs(r.latenciesNs()[n - 1]));
        System.out.printf("  min              : %s%n", fmtNs(r.latenciesNs()[0]));
        System.out.println("─────────────────────────────────────────────");
        System.out.println("  Decisions:");
        for (var d : Decision.values()) {
            long count = r.decisionCounts().getOrDefault(d, 0L);
            System.out.printf("    %-10s : %,d  (%.1f %%)%n",
                    d.name(), count, count * 100.0 / n);
        }
        System.out.printf("  Fallbacks applied: %,d  (%.1f %%)%n",
                r.fallbackCount(), r.fallbackCount() * 100.0 / n);
        System.out.println("─────────────────────────────────────────────");

        // ASCII histogram (20 buckets)
        System.out.println("  Latency histogram (ns):");
        printHistogram(r.latenciesNs(), 20);
        System.out.println("─────────────────────────────────────────────");
    }

    private static long percentile(long[] sorted, double pct) {
        int idx = (int) Math.ceil(pct / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(idx, sorted.length - 1))];
    }

    private static String fmtNs(long ns) {
        if (ns < 1_000) return ns + " ns";
        if (ns < 1_000_000) return String.format("%.2f µs", ns / 1_000.0);
        return String.format("%.2f ms", ns / 1_000_000.0);
    }

    private static void printHistogram(long[] sorted, int buckets) {
        long min = sorted[0];
        long max = sorted[sorted.length - 1];
        long range = max - min;
        if (range == 0) range = 1;

        long[] counts = new long[buckets];
        for (long v : sorted) {
            int b = (int) ((v - min) * buckets / (range + 1));
            counts[Math.min(b, buckets - 1)]++;
        }

        long maxCount = 0;
        for (long c : counts) maxCount = Math.max(maxCount, c);

        int barWidth = 40;
        for (int i = 0; i < buckets; i++) {
            long lo = min + (long) i * range / buckets;
            long hi = min + (long) (i + 1) * range / buckets;
            int bars = maxCount == 0 ? 0 : (int) (counts[i] * barWidth / maxCount);
            System.out.printf("    [%8s - %8s] %s %,d%n",
                    fmtNs(lo), fmtNs(hi),
                    "█".repeat(bars), counts[i]);
        }
    }
}
