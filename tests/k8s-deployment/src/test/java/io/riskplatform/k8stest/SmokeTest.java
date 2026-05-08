package io.riskplatform.k8stest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the most basic happy path:
 *   - cluster reachable
 *   - chart installs
 *   - all pods become Ready
 *   - service answers /healthz inside the cluster
 */
@ExtendWith(ClusterPreflight.class)
class SmokeTest {

    private String ns;
    private KubectlClient k;
    private HelmClient h;

    @BeforeEach
    void setUp() {
        ns = Namespaces.ephemeral();
        k = new KubectlClient(ns);
        k.createNamespace();
        String chart = System.getProperty("helm.chart.path");
        h = new HelmClient(chart, ns);
    }

    @AfterEach
    void tearDown() {
        if (h != null) h.uninstall("risk-engine");
        if (k != null) k.deleteNamespace();
    }

    @Test
    void chartInstallsAndPodsBecomeReady() {
        h.install("risk-engine",
                "--set", "replicaCount=1",
                "--set", "ingress.enabled=false",
                "--set", "externalSecret.enabled=false");

        KubectlClient.waitFor(
                () -> k.podsReady("app.kubernetes.io/name=risk-engine"),
                Duration.ofMinutes(2),
                "risk-engine pods Ready");

        // Cross-check: deployment exists and reports the expected replica count.
        var dep = k.getJson("deployment", "risk-engine");
        assertThat(dep.path("status").path("readyReplicas").asInt()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void healthzEndpointAnswersInsideCluster() {
        h.install("risk-engine",
                "--set", "replicaCount=1",
                "--set", "ingress.enabled=false",
                "--set", "externalSecret.enabled=false");

        KubectlClient.waitFor(
                () -> k.podsReady("app.kubernetes.io/name=risk-engine"),
                Duration.ofMinutes(2),
                "pods Ready before curl");

        // Use a one-shot debug pod; curl /healthz on the cluster-internal Service.
        var r = k.run(Duration.ofMinutes(1),
                "run", "curl-probe", "--rm", "-i", "--restart=Never",
                "--image=curlimages/curl:8.10.1", "--",
                "curl", "-sf", "http://risk-engine:8080/healthz");
        r.assertOk("curl /healthz");
    }
}
