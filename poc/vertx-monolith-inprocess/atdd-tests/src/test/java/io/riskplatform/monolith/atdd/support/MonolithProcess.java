package io.riskplatform.monolith.atdd.support;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs the vertx-monolith-inprocess fat-jar as a child JVM process for the duration of an
 * ATDD test suite. The jar is located via the {@code monolith.jar} system property which the
 * Gradle build sets from {@code :poc:vertx-monolith-inprocess:shadowJar}.
 *
 * <p>Environment is injected at launch time so the monolith points at the Testcontainers-
 * provisioned Postgres and Floci endpoints. Optional dependencies (Valkey, Kafka, ML scorer)
 * are intentionally left unset; the adapters fall back to in-memory / no-op mode.
 *
 * <p>Lifecycle is best-effort: {@link #stop()} sends a TERM and waits up to 10 seconds before
 * forcibly destroying the process.
 */
public final class MonolithProcess implements AutoCloseable {

    private final Process process;
    private final int port;
    private final Path logFile;

    private MonolithProcess(Process process, int port, Path logFile) {
        this.process = process;
        this.port = port;
        this.logFile = logFile;
    }

    public static MonolithProcess start(int port, Map<String, String> extraEnv) throws IOException, InterruptedException {
        String jarProperty = System.getProperty("monolith.jar");
        if (jarProperty == null || jarProperty.isBlank()) {
            throw new IllegalStateException("System property 'monolith.jar' is not set. " +
                    "The Gradle build wires this from :poc:vertx-monolith-inprocess:shadowJar.");
        }
        Path jar = Path.of(jarProperty).toAbsolutePath();
        if (!Files.exists(jar)) {
            throw new IllegalStateException("Monolith fat-jar not found at " + jar +
                    ". Run :poc:vertx-monolith-inprocess:shadowJar first.");
        }

        String javaHome = System.getProperty("java.home");
        Path javaBin = Path.of(javaHome, "bin", isWindows() ? "java.exe" : "java");

        Path logFile = Files.createTempFile("monolith-atdd-", ".log");

        ProcessBuilder pb = new ProcessBuilder(
                javaBin.toString(),
                "-Xms128m", "-Xmx512m",
                "-Dhttp.port=" + port,
                "-jar", jar.toString()
        );

        Map<String, String> env = new HashMap<>(pb.environment());
        // Override only what we want; inherit the rest from the parent.
        env.put("HTTP_PORT", String.valueOf(port));
        env.putAll(extraEnv);
        pb.environment().clear();
        pb.environment().putAll(env);

        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));

        Process proc = pb.start();
        return new MonolithProcess(proc, port, logFile);
    }

    public void awaitHealthy(Duration timeout) throws IOException, InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        URI healthUri = URI.create("http://localhost:" + port + "/healthz");
        Throwable last = null;
        while (Instant.now().isBefore(deadline)) {
            if (!process.isAlive()) {
                throw new IllegalStateException("Monolith process exited prematurely (rc=" +
                        process.exitValue() + "). Log: " + tail(logFile, 60));
            }
            try {
                HttpURLConnection c = (HttpURLConnection) healthUri.toURL().openConnection();
                c.setConnectTimeout(500);
                c.setReadTimeout(500);
                int code = c.getResponseCode();
                c.disconnect();
                if (code >= 200 && code < 300) {
                    return;
                }
            } catch (IOException e) {
                last = e;
            }
            Thread.sleep(250);
        }
        throw new IllegalStateException("Monolith did not become healthy on :" + port +
                " within " + timeout + ". Last error: " + last + "\nLog tail:\n" + tail(logFile, 80));
    }

    public void stop() {
        if (process != null && process.isAlive()) {
            process.destroy();
            try {
                if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
    }

    public Path logFile() { return logFile; }

    @Override
    public void close() { stop(); }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static String tail(Path file, int maxLines) {
        try {
            List<String> all = Files.readAllLines(file);
            int from = Math.max(0, all.size() - maxLines);
            return String.join("\n", all.subList(from, all.size()));
        } catch (IOException e) {
            return "(could not read log: " + e.getMessage() + ")";
        }
    }
}
