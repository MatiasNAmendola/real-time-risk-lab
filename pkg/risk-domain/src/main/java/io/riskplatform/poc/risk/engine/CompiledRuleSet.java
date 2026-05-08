package io.riskplatform.rules.engine;

import io.riskplatform.rules.config.RulesConfig;
import io.riskplatform.rules.rule.FraudRule;
import io.riskplatform.rules.rule.RuleAction;
import io.riskplatform.rules.rule.allowlist.AllowlistRule;
import io.riskplatform.rules.rule.chargeback.ChargebackHistoryRule;
import io.riskplatform.rules.rule.combination.CombinationRule;
import io.riskplatform.rules.rule.combination.SubRule;
import io.riskplatform.rules.rule.device.DeviceFingerprintRule;
import io.riskplatform.rules.rule.international.InternationalRule;
import io.riskplatform.rules.rule.mcc.MerchantCategoryRule;
import io.riskplatform.rules.rule.threshold.ThresholdRule;
import io.riskplatform.rules.rule.timeofday.TimeOfDayRule;
import io.riskplatform.rules.rule.velocity.VelocityRule;

import java.time.DayOfWeek;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Pre-compiled set of rules derived from a RulesConfig.
 *
 * Rules are compiled once at load time into FraudRule instances, avoiding per-evaluation overhead.
 * Only enabled rules are included in the compiled set.
 */
public final class CompiledRuleSet {

    private final RulesConfig config;
    private final List<FraudRule> rules;

    private CompiledRuleSet(RulesConfig config, List<FraudRule> rules) {
        this.config = config;
        this.rules  = List.copyOf(rules);
    }

    public static CompiledRuleSet compile(RulesConfig config) {
        List<FraudRule> compiled = new ArrayList<>();
        if (config.rules() == null) return new CompiledRuleSet(config, compiled);

        for (RulesConfig.RuleDefinition def : config.rules()) {
            FraudRule rule = compileRule(def);
            if (rule != null) {
                compiled.add(rule);
            }
        }
        return new CompiledRuleSet(config, compiled);
    }

    public RulesConfig config() { return config; }
    public List<FraudRule> rules() { return rules; }

    @SuppressWarnings("unchecked")
    private static FraudRule compileRule(RulesConfig.RuleDefinition def) {
        if (def.type() == null) return null;

        String name    = def.name();
        boolean enabled = def.enabled();
        double weight  = def.weight();
        RuleAction action = parseAction(def.action());
        Map<String, Object> p = def.parameters() != null ? def.parameters() : Map.of();

        return switch (def.type()) {
            case "threshold" -> new ThresholdRule(
                    name, enabled, weight, action,
                    str(p, "field"),
                    str(p, "operator"),
                    num(p, "value").doubleValue());

            case "chargeback_history" -> new ChargebackHistoryRule(
                    name, enabled, weight, action,
                    str(p, "field"),
                    str(p, "operator"),
                    num(p, "threshold").intValue());

            case "combination" -> {
                boolean requireAll = (boolean) p.getOrDefault("requireAll", true);
                List<Map<String, Object>> subDefs =
                        (List<Map<String, Object>>) p.getOrDefault("subrules", List.of());
                List<SubRule> subs = subDefs.stream().map(CompiledRuleSet::compileSubRule).toList();
                yield new CombinationRule(name, enabled, weight, action, requireAll, subs);
            }

            case "velocity" -> new VelocityRule(
                    name, enabled, weight, action,
                    num(p, "count").intValue(),
                    num(p, "windowMinutes").intValue(),
                    str(p, "groupBy"));

            case "international" -> {
                List<String> countries = (List<String>) p.getOrDefault("restrictedCountries", List.of());
                yield new InternationalRule(name, enabled, weight, action, new HashSet<>(countries));
            }

            case "time_of_day" -> {
                int from = num(p, "hoursFrom").intValue();
                int to   = num(p, "hoursTo").intValue();
                List<String> dayNames = (List<String>) p.getOrDefault("days", List.of());
                Set<DayOfWeek> days = dayNames.stream()
                        .map(DayOfWeek::valueOf)
                        .collect(Collectors.toSet());
                yield new TimeOfDayRule(name, enabled, weight, action, from, to, days);
            }

            case "merchant_category" -> {
                List<String> codes = (List<String>) p.getOrDefault("mccCodes", List.of());
                yield new MerchantCategoryRule(name, enabled, weight, action, new HashSet<>(codes));
            }

            case "device_fingerprint" -> {
                List<String> deny = (List<String>) p.getOrDefault("denyList", List.of());
                yield new DeviceFingerprintRule(name, enabled, weight, action, new HashSet<>(deny));
            }

            case "allowlist" -> {
                List<String> ids = (List<String>) p.getOrDefault("customerIds", List.of());
                boolean override = (boolean) p.getOrDefault("override", false);
                yield new AllowlistRule(name, enabled, weight, override,
                        ids != null ? new HashSet<>(ids) : Set.of());
            }

            default -> null; // validator should have caught this
        };
    }

    private static SubRule compileSubRule(Map<String, Object> def) {
        String type = (String) def.get("type");
        String field = (String) def.get("field");
        if ("boolean".equals(type)) {
            boolean eq = (boolean) def.getOrDefault("equals", true);
            return new SubRule.BooleanSubRule(field, eq);
        } else { // threshold
            String op = (String) def.get("operator");
            Number val = (Number) def.get("value");
            return new SubRule.ThresholdSubRule(field, op, val.doubleValue());
        }
    }

    private static String str(Map<String, Object> p, String key) {
        Object v = p.get(key);
        return v != null ? v.toString() : null;
    }

    private static Number num(Map<String, Object> p, String key) {
        Object v = p.get(key);
        if (v instanceof Number n) return n;
        if (v instanceof String s) return Double.parseDouble(s);
        throw new IllegalArgumentException("Expected number for key: " + key + ", got: " + v);
    }

    private static RuleAction parseAction(String action) {
        if (action == null) return RuleAction.FLAG;
        return RuleAction.valueOf(action.toUpperCase());
    }
}
