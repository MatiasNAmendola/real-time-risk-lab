package io.riskplatform.k8stest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;

import static io.riskplatform.k8stest.TestPaths.testValues;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives a full canary rollout (20 → 50 → 100) against a real Argo Rollouts
 * controller and verifies each weight transition.
 *
 * Uses the test helm values (`values-test.yaml`) which shorten pause durations
 * to 5s so the suite finishes in a few minutes instead of the production 30s.
 */
@ExtendWith(ClusterPreflight.class)
class CanaryRolloutTest {

    private static final String ROLLOUT = "risk-engine";

    private String ns;
    private KubectlClient k;
    private HelmClient h;

    @BeforeEach
    void setUp() {
        ns = Namespaces.ephemeral();
        k = new KubectlClient(ns);
        k.createNamespace();
        h = new HelmClient(System.getProperty("helm.chart.path"), ns);
    }

    @AfterEach
    void tearDown() {
        if (h != null) h.uninstall("risk-engine");
        if (k != null) k.deleteNamespace();
    }

    @Test
    void canaryPromotesAfterSuccessfulAnalysis() {
        // 1. Initial install (v1).
        h.install(ROLLOUT,
                "--set", "image.tag=v1",
                "--set", "replicaCount=3",
                "-f", testValues());

        k.waitForValue(c -> c.rolloutStatusPhase(ROLLOUT),
                "Healthy"::equals,
                Duration.ofMinutes(2),
                "rollout v1 Healthy");

        // 2. Trigger v2 update — this starts the canary state machine.
        k.setImage(ROLLOUT, "risk-engine", "ghcr.io/riskplatform/risk-engine:v2");

        // 3. Step 1: setWeight 20 → wait for canary weight = 20.
        k.waitForValue(c -> c.currentCanaryWeight(ROLLOUT),
                w -> w == 20,
                Duration.ofMinutes(1),
                "canary weight = 20");

        // 4. AnalysisRun must be created (one of the templates kicks off after step 2).
        KubectlClient.waitFor(
                () -> !k.listAnalysisRuns(ROLLOUT).isEmpty(),
                Duration.ofMinutes(2),
                "AnalysisRun present");

        // 5. Step 3: setWeight 50.
        k.waitForValue(c -> c.currentCanaryWeight(ROLLOUT),
                w -> w == 50,
                Duration.ofMinutes(2),
                "canary weight = 50");

        // 6. Step 5: setWeight 100 → fully promoted.
        k.waitForValue(c -> c.currentCanaryWeight(ROLLOUT),
                w -> w == 100 || w == 0,   // some controllers reset to 0 on promotion
                Duration.ofMinutes(3),
                "canary fully promoted");

        // 7. Final state: Healthy + image is v2.
        k.waitForValue(c -> c.rolloutStatusPhase(ROLLOUT),
                "Healthy"::equals,
                Duration.ofMinutes(2),
                "rollout Healthy after promotion");

        var ro = k.getJson("rollout.argoproj.io", ROLLOUT);
        String image = ro.at("/spec/template/spec/containers/0/image").asText();
        assertThat(image).endsWith(":v2");
    }
}
