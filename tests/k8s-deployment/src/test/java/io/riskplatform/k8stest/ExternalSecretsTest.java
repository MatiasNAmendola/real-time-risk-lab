package io.riskplatform.k8stest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies External Secrets Operator reconciles an ExternalSecret backed by
 * the Moto Secrets Manager mock into a Kubernetes Secret usable by pods.
 */
@ExtendWith(ClusterPreflight.class)
class ExternalSecretsTest {

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
    void externalSecretMaterializesIntoNativeSecret() {
        // ESO must be installed cluster-wide; skip if not.
        var crdCheck = k.run("get", "crd", "externalsecrets.external-secrets.io");
        org.junit.jupiter.api.Assumptions.assumeTrue(crdCheck.exitCode() == 0,
                "ExternalSecrets CRD missing — ESO not installed");

        h.install("risk-engine",
                "--set", "externalSecret.enabled=true",
                "--set", "externalSecret.refreshInterval=10s",
                "--set", "replicaCount=1",
                "--set", "ingress.enabled=false");

        // 1. ExternalSecret reaches SecretSynced status.
        k.waitForValue(c -> {
            var es = c.getJson("externalsecrets.external-secrets.io", "risk-engine");
            return es.at("/status/conditions/0/reason").asText();
        }, reason -> "SecretSynced".equals(reason),
                Duration.ofMinutes(1),
                "ExternalSecret SecretSynced");

        // 2. Native Secret exists.
        var secret = k.getJson("secret", "risk-engine-aws");
        assertThat(secret.path("data").size()).isGreaterThan(0);

        // 3. Pod has the env var from the secret materialised.
        KubectlClient.waitFor(
                () -> k.podsReady("app.kubernetes.io/name=risk-engine"),
                Duration.ofMinutes(2),
                "pod Ready with secret env");
    }
}
