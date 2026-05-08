package io.riskplatform.rules.rule;

import io.riskplatform.rules.engine.FeatureSnapshot;
import io.riskplatform.rules.rule.timeofday.TimeOfDayRule;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TimeOfDayRule — test plan doc 20 section 1.7 (UT-TOD-01 through UT-TOD-04).
 */
class TimeOfDayRuleTest {

    private TimeOfDayRule weekendNightRule() {
        return new TimeOfDayRule("WeekendNight", true, 0.3, RuleAction.FLAG,
                22, 6, Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY));
    }

    private FeatureSnapshot atUtc(int year, int month, int day, int hour) {
        Instant t = LocalDateTime.of(year, month, day, hour, 0).toInstant(ZoneOffset.UTC);
        return FeatureSnapshot.builder().transactionTime(t).build();
    }

    // UT-TOD-01: Saturday 23:00 UTC — should trigger (in window, correct day)
    @Test
    void evaluate_returns_triggered_when_transaction_is_saturday_at_23h_utc() {
        // 2026-05-09 is a Saturday
        FeatureSnapshot snap = atUtc(2026, 5, 9, 23);
        assertThat(weekendNightRule().evaluate(snap).triggered()).isTrue();
    }

    // UT-TOD-02: Sunday 03:00 UTC — should trigger (midnight-crossing window, 03 < 06)
    @Test
    void evaluate_returns_triggered_when_transaction_is_sunday_at_3h_utc_within_midnight_window() {
        // 2026-05-10 is a Sunday
        FeatureSnapshot snap = atUtc(2026, 5, 10, 3);
        assertThat(weekendNightRule().evaluate(snap).triggered()).isTrue();
    }

    // UT-TOD-03: Saturday 12:00 UTC — outside the time window
    @Test
    void evaluate_returns_not_triggered_when_hour_is_outside_window() {
        FeatureSnapshot snap = atUtc(2026, 5, 9, 12);
        assertThat(weekendNightRule().evaluate(snap).triggered()).isFalse();
    }

    // UT-TOD-04: Wednesday 23:00 — right hour but wrong day
    @Test
    void evaluate_returns_not_triggered_when_day_is_not_in_configured_days() {
        // 2026-05-06 is a Wednesday
        FeatureSnapshot snap = atUtc(2026, 5, 6, 23);
        assertThat(weekendNightRule().evaluate(snap).triggered()).isFalse();
    }
}
