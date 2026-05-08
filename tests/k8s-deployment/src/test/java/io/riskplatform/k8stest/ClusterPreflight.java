package io.riskplatform.k8stest;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.concurrent.TimeUnit;

/**
 * JUnit condition: skip the entire test class when no Kubernetes context is
 * reachable. Avoids cryptic failures on dev hosts without k3d/OrbStack.
 */
public final class ClusterPreflight implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext ctx) {
        try {
            Process p = new ProcessBuilder("kubectl", "cluster-info")
                    .redirectErrorStream(true).start();
            if (!p.waitFor(10, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return ConditionEvaluationResult.disabled("kubectl cluster-info timed out");
            }
            if (p.exitValue() != 0) {
                return ConditionEvaluationResult.disabled("kubectl cluster-info failed — no cluster reachable");
            }
            return ConditionEvaluationResult.enabled("Cluster reachable");
        } catch (Exception e) {
            return ConditionEvaluationResult.disabled("kubectl not available: " + e.getMessage());
        }
    }
}
