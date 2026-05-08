package io.riskplatform.k8stest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the default Kubernetes {@code RollingUpdate} strategy provides
 * zero-downtime by hammering /healthz throughout an image bump and asserting
 * that no probe sees a non-2xx response.
 */
@ExtendWith(ClusterPreflight.class)
class RollingUpdateTest {

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
    void rollingUpdateHasZeroDowntime() throws Exception {
        // Force the chart into Deployment-mode (no Argo Rollouts) for this test.
        h.install("risk-engine",
                "--set", "rollouts.enabled=false",
                "--set", "image.tag=v1",
                "--set", "replicaCount=3",
                "--set", "externalSecret.enabled=false",
                "--set", "ingress.enabled=false");

        KubectlClient.waitFor(
                () -> k.podsReady("app.kubernetes.io/name=risk-engine"),
                Duration.ofMinutes(2),
                "v1 Ready");

        // Background prober: hits Service every 250ms via a sidecar curl pod.
        AtomicInteger failures = new AtomicInteger();
        Thread prober = new Thread(() -> {
            long deadline = System.currentTimeMillis() + 90_000;
            while (System.currentTimeMillis() < deadline) {
                var r = k.run(Duration.ofSeconds(5),
                        "run", "probe-" + System.nanoTime(),
                        "--rm", "-i", "--restart=Never",
                        "--image=curlimages/curl:8.10.1", "--",
                        "curl", "-sf", "-o", "/dev/null", "-w", "%{http_code}",
                        "http://risk-engine:8080/healthz");
                if (r.exitCode() != 0) failures.incrementAndGet();
                try { Thread.sleep(250); } catch (InterruptedException e) { return; }
            }
        });
        prober.setDaemon(true);
        prober.start();

        // Trigger rolling update.
        k.run("set", "image", "deployment/risk-engine", "risk-engine=ghcr.io/riskplatform/risk-engine:v2")
                .assertOk("set image v2");
        k.run(Duration.ofMinutes(3), "rollout", "status", "deployment/risk-engine", "--timeout=2m")
                .assertOk("rollout status");

        prober.join(5_000);

        assertThat(failures.get())
                .as("RollingUpdate should be zero-downtime")
                .isZero();
    }
}
