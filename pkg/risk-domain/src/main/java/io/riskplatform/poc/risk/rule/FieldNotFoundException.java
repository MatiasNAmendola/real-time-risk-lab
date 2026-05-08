package io.riskplatform.rules.rule;

/**
 * Thrown when a rule requires a field that is absent from the FeatureSnapshot.
 * The message format is canonical and tested: "Field 'fieldName' not found in snapshot".
 */
public class FieldNotFoundException extends RuntimeException {
    public FieldNotFoundException(String fieldName) {
        super("Field '" + fieldName + "' not found in snapshot");
    }
}
