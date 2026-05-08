package io.riskplatform.bench.distributed;

import java.util.Arrays;

/**
 * Minimal latency histogram without external dependencies.
 *
 * Stores raw latency samples in nanoseconds, sorts on demand,
 * and computes percentiles via rank-based interpolation.
 *
 * Thread-safety: collect() is NOT thread-safe.
 * Callers accumulate per-thread arrays and merge at the end.
 */
public final class HdrHistogramReporter {

    private long[] samples;
    private int size;
    private boolean sorted;

    public HdrHistogramReporter(int initialCapacity) {
        this.samples = new long[initialCapacity];
        this.size    = 0;
        this.sorted  = false;
    }

    /** Record one latency measurement in nanoseconds. */
    public void record(long nanos) {
        if (size == samples.length) {
            samples = Arrays.copyOf(samples, samples.length * 2);
        }
        samples[size++] = nanos;
        sorted = false;
    }

    /** Merge another reporter's data into this one (used by runner to aggregate). */
    public void merge(HdrHistogramReporter other) {
        other.ensureSorted();
        int newSize = size + other.size;
        if (newSize > samples.length) {
            samples = Arrays.copyOf(samples, newSize);
        }
        System.arraycopy(other.samples, 0, samples, size, other.size);
        size = newSize;
        sorted = false;
    }

    /** Percentile in [0..100]. Returns nanoseconds. */
    public long percentile(double pct) {
        ensureSorted();
        if (size == 0) return 0L;
        int idx = (int) Math.ceil(pct / 100.0 * size) - 1;
        return samples[Math.max(0, Math.min(idx, size - 1))];
    }

    public long min()  { ensureSorted(); return size > 0 ? samples[0]         : 0L; }
    public long max()  { ensureSorted(); return size > 0 ? samples[size - 1]   : 0L; }
    public int  count(){ return size; }

    public double mean() {
        if (size == 0) return 0.0;
        long sum = 0;
        for (int i = 0; i < size; i++) sum += samples[i];
        return (double) sum / size;
    }

    /** ASCII bar chart (buckets wide), printed to stdout. */
    public void printHistogram(int buckets) {
        ensureSorted();
        if (size == 0) return;
        long lo  = samples[0];
        long hi  = samples[size - 1];
        long rng = hi - lo;
        if (rng == 0) rng = 1;

        long[] counts = new long[buckets];
        for (int i = 0; i < size; i++) {
            int b = (int) ((samples[i] - lo) * buckets / (rng + 1));
            counts[Math.min(b, buckets - 1)]++;
        }
        long maxCount = 0;
        for (long c : counts) maxCount = Math.max(maxCount, c);
        int barWidth = 40;
        for (int i = 0; i < buckets; i++) {
            long blo = lo + (long) i * rng / buckets;
            long bhi = lo + (long) (i + 1) * rng / buckets;
            int bars = maxCount == 0 ? 0 : (int) (counts[i] * barWidth / maxCount);
            System.out.printf("    [%8s - %8s] %s %,d%n",
                fmtNs(blo), fmtNs(bhi), "#".repeat(bars), counts[i]);
        }
    }

    private void ensureSorted() {
        if (!sorted) {
            Arrays.sort(samples, 0, size);
            sorted = true;
        }
    }

    public static String fmtNs(long ns) {
        if (ns < 1_000L)       return ns + " ns";
        if (ns < 1_000_000L)   return String.format("%.2f us", ns / 1_000.0);
        if (ns < 1_000_000_000L) return String.format("%.2f ms", ns / 1_000_000.0);
        return String.format("%.3f s", ns / 1_000_000_000.0);
    }
}
