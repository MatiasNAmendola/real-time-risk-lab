package io.riskplatform.k8stest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates the alternate {@code blueGreen} strategy by applying a
 * stand-alone Rollout manifest (the production chart uses canary; this test
 * pins blueGreen so the contract is exercised).
 *
 * Flow: deploy v1 (active=blue) → push v2 (preview=green) → promote → assert
 * activeService selector now points at green ReplicaSet.
 */
@ExtendWith(ClusterPreflight.class)
class BlueGreenTest {

    private static final String ROLLOUT = "risk-engine-bg";

    private String ns;
    private KubectlClient k;

    @BeforeEach
    void setUp() {
        ns = Namespaces.ephemeral();
        k = new KubectlClient(ns);
        k.createNamespace();
    }

    @AfterEach
    void tearDown() {
        if (k != null) {
            k.delete("rollout.argoproj.io", ROLLOUT);
            k.deleteNamespace();
        }
    }

    @Test
    void bluegreenPromotionSwapsActiveService() {
        k.apply(Path.of("src/test/resources/manifests/rollout-bluegreen.yaml"));

        // v1 becomes active.
        k.waitForValue(c -> c.rolloutStatusPhase(ROLLOUT),
                "Healthy"::equals,
                Duration.ofMinutes(2),
                "blueGreen v1 Healthy");

        var beforeHash = k.getJson("rollout.argoproj.io", ROLLOUT)
                .path("status").path("currentPodHash").asText();

        // Push v2 — preview ReplicaSet spins up but activeService stays on blue.
        k.setImage(ROLLOUT, "risk-engine", "ghcr.io/riskplatform/risk-engine:v2");

        // Promote (autoPromotionEnabled: true in the manifest → wait for swap).
        k.waitForValue(c -> {
            var ro = c.getJson("rollout.argoproj.io", ROLLOUT);
            return ro.path("status").path("currentPodHash").asText();
        }, hash -> !hash.isEmpty() && !hash.equals(beforeHash),
                Duration.ofMinutes(3),
                "currentPodHash advances → green active");

        // Active selector now references the new pod-template-hash.
        var svc = k.getJson("service", ROLLOUT + "-active");
        String activeHash = svc.path("spec").path("selector")
                .path("rollouts-pod-template-hash").asText();
        assertThat(activeHash).isNotBlank().isNotEqualTo(beforeHash);
    }
}
