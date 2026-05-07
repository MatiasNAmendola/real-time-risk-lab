package com.naranjax.poc.risk.rule.timeofday;

import com.naranjax.poc.risk.engine.FeatureSnapshot;
import com.naranjax.poc.risk.rule.FraudRule;
import com.naranjax.poc.risk.rule.RuleAction;
import com.naranjax.poc.risk.rule.RuleEvaluation;

import java.time.DayOfWeek;
import java.util.Set;

/**
 * Triggers when the transaction falls within a time window on specified days of the week.
 *
 * Supports midnight-crossing windows: hoursFrom=22, hoursTo=6 means
 * "hour >= 22 OR hour < 6" (i.e., 22:00 through 05:59 next day).
 */
public final class TimeOfDayRule implements FraudRule {

    private final String ruleName;
    private final boolean ruleEnabled;
    private final double weight;
    private final RuleAction action;
    private final int hoursFrom;
    private final int hoursTo;
    private final Set<DayOfWeek> days;

    public TimeOfDayRule(String ruleName, boolean enabled, double weight, RuleAction action,
                         int hoursFrom, int hoursTo, Set<DayOfWeek> days) {
        this.ruleName    = ruleName;
        this.ruleEnabled = enabled;
        this.weight      = weight;
        this.action      = action;
        this.hoursFrom   = hoursFrom;
        this.hoursTo     = hoursTo;
        this.days        = Set.copyOf(days);
    }

    @Override
    public String name() { return ruleName; }

    @Override
    public boolean enabled() { return ruleEnabled; }

    @Override
    public RuleEvaluation evaluate(FeatureSnapshot snapshot) {
        DayOfWeek dow = snapshot.transactionDayOfWeek();
        if (dow == null) {
            return RuleEvaluation.notTriggered(ruleName, "transactionTime is null", weight, action);
        }

        int hour = snapshot.transactionHourUtc();

        boolean dayMatches  = days.contains(dow);
        boolean hourMatches = isWithinWindow(hour);

        if (dayMatches && hourMatches) {
            return RuleEvaluation.triggered(ruleName,
                    "transaction at " + hour + ":xx on " + dow + " is within window ["
                            + hoursFrom + "-" + hoursTo + "]",
                    weight, action);
        }
        return RuleEvaluation.notTriggered(ruleName,
                "day=" + dow + " hour=" + hour + " not in window", weight, action);
    }

    private boolean isWithinWindow(int hour) {
        if (hoursFrom < hoursTo) {
            // Normal window (e.g., 08:00–18:00): hour >= from AND hour < to
            return hour >= hoursFrom && hour < hoursTo;
        } else {
            // Midnight-crossing window (e.g., 22:00–06:00): hour >= from OR hour < to
            return hour >= hoursFrom || hour < hoursTo;
        }
    }
}
