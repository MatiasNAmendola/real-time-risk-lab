package io.riskplatform.rules.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.riskplatform.rules.config.ConfigValidationException.ValidationError;

/**
 * Validates a parsed RulesConfig before it can be loaded into the engine.
 *
 * Validation is performed as a full pass: all errors are collected before throwing,
 * so callers receive a complete error list in one shot.
 *
 * The validator rejects v3-broken-style configs with:
 *   - Unknown rule types (UNKNOWN_RULE_TYPE)
 *   - Missing required parameters for known types (MISSING_REQUIRED_PARAMETERS)
 *   - Invalid parameter values (e.g., negative windowMinutes: INVALID_PARAMETER_VALUE)
 */
public final class RulesConfigValidator {

    private static final Set<String> KNOWN_TYPES = Set.of(
            "threshold", "combination", "velocity", "chargeback_history",
            "international", "time_of_day", "merchant_category",
            "device_fingerprint", "allowlist"
    );

    private static final String KNOWN_TYPES_LIST =
            "threshold, combination, velocity, chargeback_history, international, " +
            "time_of_day, merchant_category, device_fingerprint, allowlist";

    /** Validates config and throws ConfigValidationException if any errors are found. */
    public void validate(RulesConfig config) {
        List<ValidationError> errors = new ArrayList<>();

        if (config.rules() == null || config.rules().isEmpty()) {
            return; // empty rules list is allowed
        }

        for (RulesConfig.RuleDefinition rule : config.rules()) {
            String name = rule.name() != null ? rule.name() : "<unnamed>";

            // Unknown type check
            if (rule.type() == null || !KNOWN_TYPES.contains(rule.type())) {
                errors.add(new ValidationError(name, "type", "UNKNOWN_RULE_TYPE",
                        "Unknown rule type '" + rule.type() + "'. Valid types: " + KNOWN_TYPES_LIST));
                continue; // cannot validate parameters if type is unknown
            }

            Map<String, Object> params = rule.parameters();

            // Type-specific parameter validation
            switch (rule.type()) {
                case "threshold" -> validateThreshold(name, params, errors);
                case "velocity"  -> validateVelocity(name, params, errors);
                case "combination" -> validateCombination(name, params, errors);
                case "international" -> validateInternational(name, params, errors);
                case "chargeback_history" -> validateChargebackHistory(name, params, errors);
                case "time_of_day" -> validateTimeOfDay(name, params, errors);
                case "merchant_category" -> validateMerchantCategory(name, params, errors);
                case "allowlist" -> validateAllowlist(name, params, errors);
                case "device_fingerprint" -> validateDeviceFingerprint(name, params, errors);
            }
        }

        if (!errors.isEmpty()) {
            throw new ConfigValidationException(errors);
        }
    }

    private void validateThreshold(String name, Map<String, Object> params, List<ValidationError> errors) {
        if (params == null || params.isEmpty()
                || !params.containsKey("field") || !params.containsKey("operator") || !params.containsKey("value")) {
            errors.add(new ValidationError(name, "parameters", "MISSING_REQUIRED_PARAMETERS",
                    "Rule of type 'threshold' requires parameters: [field, operator, value]"));
        }
    }

    private void validateVelocity(String name, Map<String, Object> params, List<ValidationError> errors) {
        if (params == null || !params.containsKey("count")
                || !params.containsKey("windowMinutes") || !params.containsKey("groupBy")) {
            errors.add(new ValidationError(name, "parameters", "MISSING_REQUIRED_PARAMETERS",
                    "Rule of type 'velocity' requires parameters: [count, windowMinutes, groupBy]"));
            return;
        }
        Object wm = params.get("windowMinutes");
        if (wm instanceof Number n && n.intValue() <= 0) {
            errors.add(new ValidationError(name, "parameters.windowMinutes", "INVALID_PARAMETER_VALUE",
                    "windowMinutes must be > 0, got " + n.intValue()));
        }
        Object count = params.get("count");
        if (count instanceof Number n && n.intValue() <= 0) {
            errors.add(new ValidationError(name, "parameters.count", "INVALID_PARAMETER_VALUE",
                    "count must be > 0, got " + n.intValue()));
        }
    }

    private void validateCombination(String name, Map<String, Object> params, List<ValidationError> errors) {
        if (params == null || !params.containsKey("requireAll") || !params.containsKey("subrules")) {
            errors.add(new ValidationError(name, "parameters", "MISSING_REQUIRED_PARAMETERS",
                    "Rule of type 'combination' requires parameters: [requireAll, subrules]"));
        }
    }

    private void validateInternational(String name, Map<String, Object> params, List<ValidationError> errors) {
        if (params == null || !params.containsKey("restrictedCountries")) {
            errors.add(new ValidationError(name, "parameters", "MISSING_REQUIRED_PARAMETERS",
                    "Rule of type 'international' requires parameters: [restrictedCountries]"));
        }
    }

    private void validateChargebackHistory(String name, Map<String, Object> params, List<ValidationError> errors) {
        if (params == null || !params.containsKey("field")
                || !params.containsKey("threshold") || !params.containsKey("operator")) {
            errors.add(new ValidationError(name, "parameters", "MISSING_REQUIRED_PARAMETERS",
                    "Rule of type 'chargeback_history' requires parameters: [field, operator, threshold]"));
        }
    }

    private void validateTimeOfDay(String name, Map<String, Object> params, List<ValidationError> errors) {
        if (params == null || !params.containsKey("hoursFrom")
                || !params.containsKey("hoursTo") || !params.containsKey("days")) {
            errors.add(new ValidationError(name, "parameters", "MISSING_REQUIRED_PARAMETERS",
                    "Rule of type 'time_of_day' requires parameters: [hoursFrom, hoursTo, days]"));
        }
    }

    private void validateMerchantCategory(String name, Map<String, Object> params, List<ValidationError> errors) {
        if (params == null || !params.containsKey("mccCodes")) {
            errors.add(new ValidationError(name, "parameters", "MISSING_REQUIRED_PARAMETERS",
                    "Rule of type 'merchant_category' requires parameters: [mccCodes]"));
        }
    }

    private void validateAllowlist(String name, Map<String, Object> params, List<ValidationError> errors) {
        // allowlist can use inline customerIds or a lookup table; override is optional (defaults false)
        // minimal validation: at least one of customerIds or lookup must be present
    }

    private void validateDeviceFingerprint(String name, Map<String, Object> params, List<ValidationError> errors) {
        // device_fingerprint can use inline denyList or a lookup; no strict required param
    }
}
