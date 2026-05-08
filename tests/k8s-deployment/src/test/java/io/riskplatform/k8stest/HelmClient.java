package io.riskplatform.k8stest;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Minimal helm CLI wrapper. */
public final class HelmClient {

    private final String chartPath;
    private final String namespace;

    public HelmClient(String chartPath, String namespace) {
        this.chartPath = chartPath;
        this.namespace = namespace;
    }

    public void install(String release, String... extraArgs) {
        List<String> cmd = new ArrayList<>(Arrays.asList(
                "helm", "install", release, chartPath,
                "--namespace", namespace, "--create-namespace",
                "--wait", "--timeout", "2m"));
        cmd.addAll(Arrays.asList(extraArgs));
        run(cmd, 180);
    }

    public void upgrade(String release, String... extraArgs) {
        List<String> cmd = new ArrayList<>(Arrays.asList(
                "helm", "upgrade", release, chartPath,
                "--namespace", namespace,
                "--reuse-values",
                "--wait", "--timeout", "2m"));
        cmd.addAll(Arrays.asList(extraArgs));
        run(cmd, 180);
    }

    public void uninstall(String release) {
        run(Arrays.asList("helm", "uninstall", release, "--namespace", namespace,
                "--ignore-not-found", "--wait"), 120);
    }

    private static void run(List<String> cmd, int timeoutSec) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(false);
            Process p = pb.start();
            if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new RuntimeException("helm timed out: " + String.join(" ", cmd));
            }
            if (p.exitValue() != 0) {
                String err = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                throw new RuntimeException("helm failed: " + String.join(" ", cmd) + " :: " + err);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
