package io.riskplatform.k8stest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts that the project's ArgoCD Application reaches Synced + Healthy.
 *
 * Pre-condition: argocd namespace exists and `application-risk-engine.yaml`
 * has been applied by `./nx up k8s`. We do not create the Application here —
 * we observe its convergence.
 */
@ExtendWith(ClusterPreflight.class)
class ArgoCDSyncTest {

    private final KubectlClient argocd = new KubectlClient("argocd");

    @BeforeEach
    void preflight() {
        // Skip silently if ArgoCD is not installed in this cluster.
        var r = argocd.run("get", "applications.argoproj.io", "risk-engine");
        org.junit.jupiter.api.Assumptions.assumeTrue(
                r.exitCode() == 0,
                "ArgoCD Application 'risk-engine' not present — skipping");
    }

    @AfterEach
    void noop() { /* read-only test */ }

    @Test
    void applicationReachesSyncedAndHealthy() {
        argocd.waitForValue(c -> {
            var app = c.getJson("applications.argoproj.io", "risk-engine");
            String sync = app.path("status").path("sync").path("status").asText();
            String health = app.path("status").path("health").path("status").asText();
            return sync + "|" + health;
        }, s -> s.equals("Synced|Healthy"),
                Duration.ofMinutes(3),
                "Application Synced+Healthy");

        var app = argocd.getJson("applications.argoproj.io", "risk-engine");
        assertThat(app.at("/status/operationState/phase").asText())
                .isIn("Succeeded", "");  // empty if no operation in flight
    }
}
