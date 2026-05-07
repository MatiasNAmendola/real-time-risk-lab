package com.naranjax.bench.distributed;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * HTTP load generator for the Vert.x distributed PoC (controller-app).
 *
 * Uses virtual threads (Java 21+) for high concurrency without thread pool sizing.
 * Fires N requests with M concurrent "users", measures per-request latency,
 * aggregates into a histogram, and writes a JSON result file.
 *
 * Usage:
 *   java -jar target/distributed-bench.jar [N] [M] [baseUrl] [outDir]
 *
 *   N       = total requests     (default 5000)
 *   M       = concurrent threads (default 32)
 *   baseUrl = controller-app URL (default http://localhost:8080)
 *   outDir  = output directory   (default out/distributed)
 *
 * Exit codes:
 *   0 = success
 *   1 = controller-app unreachable (docker compose not up)
 *   2 = too many errors (> 5 %)
 */
public final class DistributedBenchmark {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final long[] AMOUNTS_CENTS = {
        1_000L, 5_000L, 15_000L, 50_000L, 150_000L, 200_000L, 500_000L
    };

    public static void main(String[] args) throws Exception {
        int    n       = args.length > 0 ? Integer.parseInt(args[0]) : 5_000;
        int    m       = args.length > 1 ? Integer.parseInt(args[1]) : 32;
        String baseUrl = args.length > 2 ? args[2] : "http://localhost:8080";
        String outDir  = args.length > 3 ? args[3] : "out/distributed";

        System.out.printf("=== Distributed Bench ===%n");
        System.out.printf("requests=%d  concurrency=%d  target=%s%n%n", n, m, baseUrl);

        // ── Connectivity check ─────────────────────────────────────────────────
        if (!isReachable(baseUrl)) {
            System.err.println("ERROR: controller-app is not reachable at " + baseUrl + "/health");
            System.err.println("       Start the Vert.x stack first:");
            System.err.println("         cd poc/java-vertx-distributed && docker compose up -d");
            System.exit(1);
        }

        // ── Run ────────────────────────────────────────────────────────────────
        var result = run(n, m, baseUrl);
        var histogram = result.histogram();

        // ── Report to stdout ───────────────────────────────────────────────────
        double wallMs    = result.wallNs() / 1_000_000.0;
        double throughput = n / (result.wallNs() / 1_000_000_000.0);

        System.out.println();
        System.out.println("-------------------------------------------------");
        System.out.printf("  Total wall time  : %,.1f ms%n",  wallMs);
        System.out.printf("  Throughput       : %,.0f req/s%n", throughput);
        System.out.printf("  Errors           : %d  (%.2f %%)%n",
            result.errors(), result.errors() * 100.0 / n);
        System.out.println("-------------------------------------------------");
        System.out.printf("  p50              : %s%n", HdrHistogramReporter.fmtNs(histogram.percentile(50)));
        System.out.printf("  p95              : %s%n", HdrHistogramReporter.fmtNs(histogram.percentile(95)));
        System.out.printf("  p99              : %s%n", HdrHistogramReporter.fmtNs(histogram.percentile(99)));
        System.out.printf("  p99.9            : %s%n", HdrHistogramReporter.fmtNs(histogram.percentile(99.9)));
        System.out.printf("  max              : %s%n", HdrHistogramReporter.fmtNs(histogram.max()));
        System.out.printf("  min              : %s%n", HdrHistogramReporter.fmtNs(histogram.min()));
        System.out.printf("  mean             : %s%n", HdrHistogramReporter.fmtNs((long) histogram.mean()));
        System.out.println("-------------------------------------------------");
        System.out.println("  Latency histogram:");
        histogram.printHistogram(20);
        System.out.println("-------------------------------------------------");

        // ── Persist JSON ───────────────────────────────────────────────────────
        String ts = String.valueOf(System.currentTimeMillis());
        Path dir  = Path.of(outDir);
        Files.createDirectories(dir);
        Path outFile = dir.resolve(ts + ".json");
        var json = Map.ofEntries(
            Map.entry("timestamp",     Instant.now().toString()),
            Map.entry("type",          "distributed"),
            Map.entry("target",        baseUrl),
            Map.entry("requests",      n),
            Map.entry("concurrency",   m),
            Map.entry("wallMs",        wallMs),
            Map.entry("throughputRps", throughput),
            Map.entry("errors",        result.errors()),
            Map.entry("p50Ms",         histogram.percentile(50)   / 1_000_000.0),
            Map.entry("p95Ms",         histogram.percentile(95)   / 1_000_000.0),
            Map.entry("p99Ms",         histogram.percentile(99)   / 1_000_000.0),
            Map.entry("p999Ms",        histogram.percentile(99.9) / 1_000_000.0),
            Map.entry("maxMs",         histogram.max()            / 1_000_000.0),
            Map.entry("minMs",         histogram.min()            / 1_000_000.0),
            Map.entry("meanMs",        histogram.mean()           / 1_000_000.0)
        );
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(outFile.toFile(), json);
        System.out.println("Result written to: " + outFile.toAbsolutePath());

        // ── Exit with error if error rate > 5 % ───────────────────────────────
        if (result.errors() * 100.0 / n > 5.0) {
            System.err.printf("ERROR: error rate %.2f %% exceeds 5 %% threshold%n",
                result.errors() * 100.0 / n);
            System.exit(2);
        }
    }

    // ─── Core execution ─────────────────────────────────────────────────────────

    private record RunResult(HdrHistogramReporter histogram, long wallNs, long errors) {}

    private static RunResult run(int n, int m, String baseUrl) throws Exception {
        var client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();

        var perThread = new ConcurrentHashMap<Long, HdrHistogramReporter>();
        var semaphore = new Semaphore(m);
        var latch     = new CountDownLatch(n);
        var errors    = new AtomicLong(0);
        var rng       = new Random(System.currentTimeMillis());

        long wallStart = System.nanoTime();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < n; i++) {
                final long amountCents = AMOUNTS_CENTS[rng.nextInt(AMOUNTS_CENTS.length)];
                final String txId      = "tx-dist-" + i + "-" + UUID.randomUUID();
                final String custId    = "user-" + (i % 1000);

                semaphore.acquire();
                executor.submit(() -> {
                    var localHistogram = perThread.computeIfAbsent(
                        Thread.currentThread().threadId(),
                        k -> new HdrHistogramReporter(256)
                    );
                    try {
                        String body = String.format(
                            "{\"transactionId\":\"%s\",\"customerId\":\"%s\",\"amountCents\":%d}",
                            txId, custId, amountCents
                        );
                        var req = HttpRequest.newBuilder()
                            .uri(URI.create(baseUrl + "/risk"))
                            .header("Content-Type", "application/json")
                            .header("X-Correlation-Id", UUID.randomUUID().toString())
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .timeout(Duration.ofSeconds(20))
                            .build();

                        long t0 = System.nanoTime();
                        var resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                        long elapsed = System.nanoTime() - t0;

                        localHistogram.record(elapsed);
                        if (resp.statusCode() != 200) errors.incrementAndGet();

                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        semaphore.release();
                        latch.countDown();
                    }
                });
            }
            latch.await();
        }

        long wallNs = System.nanoTime() - wallStart;

        // Merge per-thread histograms
        var merged = new HdrHistogramReporter(n);
        for (var h : perThread.values()) merged.merge(h);
        return new RunResult(merged, wallNs, errors.get());
    }

    // ─── Connectivity check ──────────────────────────────────────────────────────

    private static boolean isReachable(String baseUrl) {
        try {
            var client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
            var req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/health"))
                .GET()
                .timeout(Duration.ofSeconds(3))
                .build();
            var resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
