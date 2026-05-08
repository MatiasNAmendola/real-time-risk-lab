package io.riskplatform.bench.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.StreamSupport;

/**
 * Reads the latest JSON output from inprocess-bench and distributed-bench,
 * then generates a side-by-side comparison in Markdown, JSON, and plain text.
 *
 * Does NOT execute the benchmarks — call this after both have produced output.
 * See scripts/run-comparison.sh for the full orchestration.
 *
 * Usage:
 *   java -jar build/libs/runner-0.1.0-SNAPSHOT.jar [inprocessDir] [distributedDir] [outDir]
 *
 * Defaults:
 *   inprocessDir  = ../out/perf  (JMH JSON files written by inprocess-bench main)
 *   distributedDir = ../out/distributed
 *   outDir        = ../out/perf/<timestamp>/
 */
public final class ComparisonRunner {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        String inpDir  = args.length > 0 ? args[0] : "out/perf";
        String distDir = args.length > 1 ? args[1] : "out/distributed";
        String baseOut = args.length > 2 ? args[2] : "out/perf";

        // ── Load latest result files ───────────────────────────────────────────
        var inpNode  = latestJson(Path.of(inpDir),  "inprocess-jmh-");
        var distNode = latestJson(Path.of(distDir), "");       // any .json

        // ── Extract metrics ────────────────────────────────────────────────────
        Metrics inp  = extractInprocess(inpNode);
        Metrics dist = extractDistributed(distNode);

        // ── Output dir ────────────────────────────────────────────────────────
        String ts  = String.valueOf(System.currentTimeMillis());
        Path outDir = Path.of(baseOut, ts);
        Files.createDirectories(outDir);

        // ── Generate report ────────────────────────────────────────────────────
        String md  = buildMarkdown(inp, dist);
        String txt = buildText(inp, dist);
        ObjectNode json = buildJson(inp, dist);

        Files.writeString(outDir.resolve("comparison.md"),   md);
        Files.writeString(outDir.resolve("comparison.txt"),  txt);
        MAPPER.writerWithDefaultPrettyPrinter()
              .writeValue(outDir.resolve("comparison.json").toFile(), json);

        System.out.println("Comparison written to: " + outDir.toAbsolutePath());
        System.out.println();
        System.out.print(txt);
    }

    // ─── Data extraction ─────────────────────────────────────────────────────────

    /**
     * JMH result JSON is an array of benchmark entries.
     * We look for the SampleTime mode to get p50/p95/p99.
     */
    private static Metrics extractInprocess(Optional<JsonNode> nodeOpt) {
        if (nodeOpt.isEmpty()) return Metrics.placeholder("in-process (no result yet)");
        JsonNode root = nodeOpt.get();

        // JMH format: array of {benchmark, mode, primaryMetric: {scorePercentiles: {"50.0":..., "95.0":..., "99.0":...}}}
        // Fall back to AverageTime score if percentiles absent.
        if (root.isArray()) {
            for (JsonNode entry : root) {
                String mode = entry.path("mode").asText("");
                if ("sample".equals(mode)) {
                    JsonNode pct = entry.path("primaryMetric").path("scorePercentiles");
                    double p50  = pct.path("50.0").asDouble(-1);
                    double p95  = pct.path("95.0").asDouble(-1);
                    double p99  = pct.path("99.0").asDouble(-1);
                    // JMH SampleTime in ms by default (OutputTimeUnit.MILLISECONDS in benchmark)
                    return new Metrics(p50, p95, p99, -1, -1, "in-process (bare-javac)");
                }
                if ("thrpt".equals(mode)) {
                    // ops/ms * 1000 = ops/s
                    double opsPerMs = entry.path("primaryMetric").path("score").asDouble(-1);
                    return new Metrics(-1, -1, -1, opsPerMs * 1000, -1, "in-process (bare-javac)");
                }
            }
        }
        return Metrics.placeholder("in-process (unrecognized JMH format)");
    }

    private static Metrics extractDistributed(Optional<JsonNode> nodeOpt) {
        if (nodeOpt.isEmpty()) return Metrics.placeholder("distributed (no result yet)");
        JsonNode root = nodeOpt.get();
        return new Metrics(
            root.path("p50Ms").asDouble(-1),
            root.path("p95Ms").asDouble(-1),
            root.path("p99Ms").asDouble(-1),
            root.path("throughputRps").asDouble(-1),
            -1,
            "distributed (Vert.x layer-as-pod)"
        );
    }

    // ─── Report builders ─────────────────────────────────────────────────────────

    private static String buildMarkdown(Metrics inp, Metrics dist) {
        String date = LocalDate.now().toString();
        return """
            # Performance comparison — %s

            | Metric | In-process (bare-javac) | Distributed (Vert.x layer-as-pod) | Overhead |
            |--------|------------------------:|-----------------------------------:|---------:|
            | p50 latency | %s | %s | %s |
            | p95 latency | %s | %s | %s |
            | p99 latency | %s | %s | %s |
            | Throughput | %s | %s | %s |
            | Containers | 1 JVM | 5 JVMs | 5x operational |

            ## Reading

            - In-process wins on base latency (no network hops). Dominant when p99 < 300 ms is a hard
              requirement and the entire logic fits a single JVM.
            - Distributed wins on isolation, independent scaling, and blast-radius containment.
              The cost is serialization + cluster overhead + 5 JVMs.
            - The fake ML scorer (~150 ms) dominates both: in real systems fix the ML latency first
              before optimizing architectural layout.

            ## Notes

            Results marked N/A require running the respective benchmark.
            See `bench/scripts/run-comparison.sh` for the full workflow.
            """.formatted(
                date,
                fmtMs(inp.p50Ms()),  fmtMs(dist.p50Ms()),  overhead(inp.p50Ms(), dist.p50Ms()),
                fmtMs(inp.p95Ms()),  fmtMs(dist.p95Ms()),  overhead(inp.p95Ms(), dist.p95Ms()),
                fmtMs(inp.p99Ms()),  fmtMs(dist.p99Ms()),  overhead(inp.p99Ms(), dist.p99Ms()),
                fmtRps(inp.rps()),   fmtRps(dist.rps()),   overheadRps(inp.rps(), dist.rps())
            );
    }

    private static String buildText(Metrics inp, Metrics dist) {
        return """
            ================================================
            Performance Comparison — %s
            ================================================
            Metric           In-Process        Distributed
            ------------------------------------------------
            p50 latency      %-16s  %s
            p95 latency      %-16s  %s
            p99 latency      %-16s  %s
            Throughput       %-16s  %s
            ------------------------------------------------
            Source (in-proc) : %s
            Source (dist)    : %s
            ================================================
            """.formatted(
                Instant.now(),
                fmtMs(inp.p50Ms()),  fmtMs(dist.p50Ms()),
                fmtMs(inp.p95Ms()),  fmtMs(dist.p95Ms()),
                fmtMs(inp.p99Ms()),  fmtMs(dist.p99Ms()),
                fmtRps(inp.rps()),   fmtRps(dist.rps()),
                inp.label(), dist.label()
            );
    }

    private static ObjectNode buildJson(Metrics inp, Metrics dist) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("generatedAt", Instant.now().toString());
        ObjectNode i = root.putObject("inprocess");
        i.put("label",       inp.label());
        i.put("p50Ms",       inp.p50Ms());
        i.put("p95Ms",       inp.p95Ms());
        i.put("p99Ms",       inp.p99Ms());
        i.put("throughputRps", inp.rps());
        ObjectNode d = root.putObject("distributed");
        d.put("label",       dist.label());
        d.put("p50Ms",       dist.p50Ms());
        d.put("p95Ms",       dist.p95Ms());
        d.put("p99Ms",       dist.p99Ms());
        d.put("throughputRps", dist.rps());
        ObjectNode o = root.putObject("overhead");
        o.put("p50",  overhead(inp.p50Ms(), dist.p50Ms()));
        o.put("p95",  overhead(inp.p95Ms(), dist.p95Ms()));
        o.put("p99",  overhead(inp.p99Ms(), dist.p99Ms()));
        o.put("rps",  overheadRps(inp.rps(), dist.rps()));
        return root;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private record Metrics(double p50Ms, double p95Ms, double p99Ms,
                           double rps, double rssKb, String label) {
        static Metrics placeholder(String label) {
            return new Metrics(-1, -1, -1, -1, -1, label);
        }
    }

    private static String fmtMs(double ms) {
        if (ms < 0) return "N/A";
        if (ms < 1) return String.format("%.0f us", ms * 1000);
        return String.format("%.1f ms", ms);
    }

    private static String fmtRps(double rps) {
        if (rps < 0) return "N/A";
        return String.format("%.0f req/s", rps);
    }

    private static String overhead(double baseMs, double distMs) {
        if (baseMs <= 0 || distMs <= 0) return "N/A";
        double factor = distMs / baseMs;
        return String.format("%.1fx", factor);
    }

    private static String overheadRps(double baseRps, double distRps) {
        if (baseRps <= 0 || distRps <= 0) return "N/A";
        double pct = (distRps - baseRps) / baseRps * 100;
        return String.format("%+.0f%%", pct);
    }

    /**
     * Finds the latest JSON file in dir whose name starts with prefix.
     * Empty prefix matches all .json files.
     */
    private static Optional<JsonNode> latestJson(Path dir, String prefix) throws IOException {
        if (!Files.isDirectory(dir)) return Optional.empty();
        try (var stream = Files.list(dir)) {
            var latest = stream
                .filter(p -> p.getFileName().toString().endsWith(".json"))
                .filter(p -> prefix.isEmpty() || p.getFileName().toString().startsWith(prefix))
                .max(Comparator.comparingLong(p -> {
                    try { return Files.getLastModifiedTime(p).toMillis(); }
                    catch (IOException e) { return 0L; }
                }));
            if (latest.isEmpty()) return Optional.empty();
            return Optional.of(MAPPER.readTree(latest.get().toFile()));
        }
    }
}
