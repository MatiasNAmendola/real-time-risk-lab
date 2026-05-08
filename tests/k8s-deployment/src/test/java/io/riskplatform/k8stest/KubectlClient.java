package io.riskplatform.k8stest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * Thin wrapper over the {@code kubectl} binary used by the k8s-deployment test
 * suite. We deliberately avoid the official Java client to minimise dependency
 * weight and to mirror the commands a human operator would run during an
 * incident — readability over abstraction.
 */
public final class KubectlClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration DEFAULT_CMD_TIMEOUT = Duration.ofSeconds(60);

    private final String namespace;

    public KubectlClient(String namespace) {
        this.namespace = namespace;
    }

    public String namespace() {
        return namespace;
    }

    public Result run(String... args) {
        return run(DEFAULT_CMD_TIMEOUT, args);
    }

    public Result run(Duration timeout, String... args) {
        List<String> cmd = new ArrayList<>();
        cmd.add("kubectl");
        cmd.add("--namespace");
        cmd.add(namespace);
        cmd.addAll(Arrays.asList(args));
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(false);
            Process p = pb.start();
            boolean ok = p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!ok) {
                p.destroyForcibly();
                throw new RuntimeException("kubectl timed out: " + String.join(" ", cmd));
            }
            String stdout = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            return new Result(p.exitValue(), stdout, stderr);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("kubectl failed: " + String.join(" ", cmd), e);
        }
    }

    public void apply(Path manifest) {
        Result r = run("apply", "-f", manifest.toAbsolutePath().toString());
        r.assertOk("apply " + manifest);
    }

    public void applyInline(String yaml) {
        try {
            Path tmp = Files.createTempFile("k8s-test-", ".yaml");
            Files.writeString(tmp, yaml);
            apply(tmp);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public JsonNode getJson(String resource, String name) {
        Result r = run("get", resource, name, "-o", "json");
        r.assertOk("get " + resource + "/" + name);
        try {
            return MAPPER.readTree(r.stdout);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void setImage(String rolloutName, String container, String image) {
        Result r = run("set", "image", "rollout/" + rolloutName, container + "=" + image);
        r.assertOk("set image " + rolloutName);
    }

    public String rolloutStatusPhase(String rolloutName) {
        return getJson("rollout.argoproj.io", rolloutName).path("status").path("phase").asText("");
    }

    public int currentCanaryWeight(String rolloutName) {
        return getJson("rollout.argoproj.io", rolloutName)
                .path("status")
                .path("canary")
                .path("weights")
                .path("canary")
                .path("weight")
                .asInt(0);
    }

    public List<String> listAnalysisRuns(String rolloutName) {
        Result r = run("get", "analysisruns",
                "-l", "rollout=" + rolloutName,
                "-o", "jsonpath={.items[*].metadata.name}");
        r.assertOk("list analysisruns");
        return Arrays.stream(r.stdout.trim().split("\\s+"))
                .filter(s -> !s.isBlank())
                .toList();
    }

    public boolean podsReady(String labelSelector) {
        Result r = run("get", "pods", "-l", labelSelector,
                "-o", "jsonpath={.items[*].status.containerStatuses[*].ready}");
        if (r.exitCode != 0) return false;
        String s = r.stdout.trim();
        if (s.isBlank()) return false;
        for (String tok : s.split("\\s+")) {
            if (!"true".equals(tok)) return false;
        }
        return true;
    }

    public void delete(String resource, String name) {
        run("delete", resource, name, "--ignore-not-found");
    }

    public void deleteNamespace() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "kubectl", "delete", "namespace", namespace,
                    "--ignore-not-found", "--wait=false");
            pb.start().waitFor(15, TimeUnit.SECONDS);
        } catch (Exception ignore) {
            // best-effort
        }
    }

    public void createNamespace() {
        ProcessBuilder pb = new ProcessBuilder(
                "kubectl", "create", "namespace", namespace);
        try {
            pb.start().waitFor(15, TimeUnit.SECONDS);
        } catch (Exception ignore) {
            // already exists is fine
        }
    }

    /** Block until {@code predicate} returns true, polling every second. */
    public static void waitFor(BooleanSupplier predicate, Duration timeout, String description) {
        Instant deadline = Instant.now().plus(timeout);
        Throwable last = null;
        while (Instant.now().isBefore(deadline)) {
            try {
                if (predicate.getAsBoolean()) return;
            } catch (Throwable t) {
                last = t;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(ie);
            }
        }
        throw new AssertionError("Timeout (" + timeout + ") waiting for: " + description, last);
    }

    public <T> T waitForValue(Function<KubectlClient, T> supplier, Function<T, Boolean> done,
                              Duration timeout, String description) {
        Instant deadline = Instant.now().plus(timeout);
        T last = null;
        while (Instant.now().isBefore(deadline)) {
            last = supplier.apply(this);
            if (done.apply(last)) return last;
            try { Thread.sleep(1000); } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(ie);
            }
        }
        throw new AssertionError("Timeout waiting for: " + description + " (last=" + last + ")");
    }

    public record Result(int exitCode, String stdout, String stderr) {
        public void assertOk(String op) {
            if (exitCode != 0) {
                throw new RuntimeException("kubectl " + op + " failed (" + exitCode + "): " + stderr);
            }
        }
    }
}
