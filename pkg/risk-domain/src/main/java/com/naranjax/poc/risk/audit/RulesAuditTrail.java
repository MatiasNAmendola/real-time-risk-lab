package com.naranjax.poc.risk.audit;

import com.naranjax.poc.risk.engine.AggregateDecision;
import com.naranjax.poc.risk.engine.FeatureSnapshot;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * In-memory, thread-safe audit trail for rule engine evaluations.
 *
 * Bounded by maxEntries (default 10_000) using a ConcurrentLinkedDeque with tail eviction.
 * In production this would dual-write to a database and emit to a Kafka topic.
 */
public final class RulesAuditTrail {

    private static final int DEFAULT_MAX = 10_000;
    private final int maxEntries;
    private final ConcurrentLinkedDeque<RulesAuditEntry> entries;
    private final AtomicLong totalRecorded = new AtomicLong(0);

    public RulesAuditTrail() {
        this(DEFAULT_MAX);
    }

    public RulesAuditTrail(int maxEntries) {
        this.maxEntries = maxEntries;
        this.entries    = new ConcurrentLinkedDeque<>();
    }

    /** Records a completed evaluation. Thread-safe. */
    public void record(FeatureSnapshot snapshot, AggregateDecision decision) {
        entries.addFirst(RulesAuditEntry.from(snapshot, decision));
        totalRecorded.incrementAndGet();

        // Evict oldest entries if we exceed the cap
        while (entries.size() > maxEntries) {
            entries.pollLast();
        }
    }

    /**
     * Returns the N most recent entries (newest first).
     *
     * @param limit maximum number of entries to return
     */
    public List<RulesAuditEntry> recent(int limit) {
        return entries.stream()
                .limit(limit)
                .collect(Collectors.toUnmodifiableList());
    }

    /** Total number of evaluations recorded (monotonically increasing). */
    public long totalRecorded() {
        return totalRecorded.get();
    }

    /** Current number of entries in the bounded buffer. */
    public int size() {
        return entries.size();
    }
}
