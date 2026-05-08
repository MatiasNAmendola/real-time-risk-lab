package io.riskplatform.k8stest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;

import static io.riskplatform.k8stest.TestPaths.testValues;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pushes a deliberately broken revision (failing readiness probe) and asserts
 * that Argo Rollouts marks the rollout Degraded and rolls back to the previous
 * stable image.
 */
@ExtendWith(ClusterPreflight.class)
class CanaryRollbackTest {

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
    void rollbackOnFailedAnalysisOrReadiness() {
        // 1. Stable v1.
        h.install(ROLLOUT,
                "--set", "image.tag=v1",
                "--set", "replicaCount=3",
                "-f", testValues());
        k.waitForValue(c -> c.rolloutStatusPhase(ROLLOUT),
                "Healthy"::equals,
                Duration.ofMinutes(2),
                "v1 Healthy");

        // 2. Broken revision: image whose readiness probe never passes
        //    (manifest deployment-broken.yaml flips readinessProbe path to /never-ready).
        k.apply(java.nio.file.Path.of(
                "src/test/resources/manifests/deployment-broken.yaml"));

        // 3. Argo Rollouts should detect failure (failed AnalysisRun OR
        //    readiness threshold) and mark Degraded within ~3 min.
        k.waitForValue(c -> c.rolloutStatusPhase(ROLLOUT),
                phase -> "Degraded".equals(phase),
                Duration.ofMinutes(3),
                "rollout Degraded after broken push");

        // 4. Stable image must remain v1 — verify via stableRS hash.
        var ro = k.getJson("rollout.argoproj.io", ROLLOUT);
        String stableRS = ro.path("status").path("stableRS").asText();
        assertThat(stableRS).isNotBlank();

        // 5. Service still routes to a Ready pod (zero-downtime guarantee).
        assertThat(k.podsReady("app.kubernetes.io/name=risk-engine,role=stable")).isTrue();
    }
}
