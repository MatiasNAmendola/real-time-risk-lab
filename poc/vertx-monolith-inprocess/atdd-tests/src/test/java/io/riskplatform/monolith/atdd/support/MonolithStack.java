package io.riskplatform.monolith.atdd.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Self-launching ATDD stack for the {@code vertx-monolith-inprocess} PoC.
 *
 * <p>Starts the minimum infrastructure the monolith requires to boot:
 * <ul>
 *   <li>Postgres 16 (hard dependency — DbBootstrap migrates the schema at startup).</li>
 *   <li>Floci unified AWS emulator on :4566 (S3 audit + SQS publish + Secrets).</li>
 *   <li>The monolith fat-jar as a child JVM on a randomly chosen free port.</li>
 * </ul>
 *
 * <p>Optional dependencies (Valkey, Kafka, ML scorer) are left unconfigured; the
 * adapters fall back to in-memory / no-op mode. Karate scenarios that exercise
 * Kafka or webhook flows are expected to be tagged and may be filtered out.
 *
 * <p>Singleton lifecycle: instantiated once per JVM (or per test suite), shared
 * across all features. Call {@link #shutdown()} from an {@code @AfterAll} hook.
 */
public final class MonolithStack implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(MonolithStack.class);
    private static volatile MonolithStack instance;

    private final PostgreSQLContainer<?> postgres;
    private final GenericContainer<?> floci;
    private final MonolithProcess monolith;
    private final int monolithPort;

    private MonolithStack(PostgreSQLContainer<?> postgres, GenericContainer<?> floci,
                          MonolithProcess monolith, int monolithPort) {
        this.postgres = postgres;
        this.floci = floci;
        this.monolith = monolith;
        this.monolithPort = monolithPort;
    }

    public static synchronized MonolithStack start() {
        if (instance != null) return instance;
        PostgreSQLContainer<?> pg = null;
        GenericContainer<?> fl = null;
        MonolithProcess proc = null;
        try {
            log.info("[atdd-stack] starting Postgres + Floci + monolith ...");

            pg = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("riskplatform")
                    .withUsername("riskplatform")
                    .withPassword("riskplatform");
            pg.start();
            log.info("[atdd-stack] Postgres ready on {}:{}", pg.getHost(), pg.getMappedPort(5432));

            fl = new GenericContainer<>(DockerImageName.parse("floci/floci:latest"))
                    .withExposedPorts(4566)
                    .withEnv("FLOCI_DEFAULT_REGION", "us-east-1")
                    .waitingFor(Wait.forHttp("/_floci/health").forPort(4566)
                            .withStartupTimeout(Duration.ofSeconds(60)));
            fl.start();
            String flociEndpoint = "http://" + fl.getHost() + ":" + fl.getMappedPort(4566);
            log.info("[atdd-stack] Floci ready at {}", flociEndpoint);

            // The monolith hardcodes HTTP_PORT=8090 in HttpServerVerticle.java. We can't
            // override it without touching production code, so we just claim :8090 here.
            // If something is already on :8090 the launch will fail fast with a clear error.
            int port = 8090;
            if (isPortInUse(port)) {
                throw new IllegalStateException("Port " + port + " is already in use. " +
                        "Stop any running monolith (docker compose down) before running ATDD tests.");
            }
            Map<String, String> env = new HashMap<>();
            env.put("PG_HOST", pg.getHost());
            env.put("PG_PORT", String.valueOf(pg.getMappedPort(5432)));
            env.put("PG_DATABASE", pg.getDatabaseName());
            env.put("PG_USER", pg.getUsername());
            env.put("PG_PASSWORD", pg.getPassword());
            env.put("FLOCI_ENDPOINT", flociEndpoint);
            env.put("AWS_ACCESS_KEY_ID", "test");
            env.put("AWS_SECRET_ACCESS_KEY", "test");
            env.put("AWS_REGION", "us-east-1");
            // Leave KAFKA_BOOTSTRAP_SERVERS, VALKEY_URL, ML_SCORER_URL unset
            // -> adapters fall back to in-memory / no-op mode.

            proc = MonolithProcess.start(port, env);
            log.info("[atdd-stack] monolith launched on :{}, awaiting /healthz ...", port);
            proc.awaitHealthy(Duration.ofSeconds(90));
            log.info("[atdd-stack] monolith healthy on :{}", port);

            // Publish for karate-config.js
            System.setProperty("monolith.baseUrl", "http://localhost:" + port);

            instance = new MonolithStack(pg, fl, proc, port);
            Runtime.getRuntime().addShutdownHook(new Thread(instance::shutdown, "monolith-stack-shutdown"));
            return instance;
        } catch (Exception e) {
            // Best-effort cleanup of anything we already started, so a failed @BeforeAll
            // doesn't leak a Postgres / Floci container or zombie monolith JVM.
            log.error("[atdd-stack] launch failed, cleaning up partial state: {}", e.getMessage());
            try { if (proc != null) proc.stop(); } catch (Exception ignored) {}
            try { if (fl != null) fl.stop(); } catch (Exception ignored) {}
            try { if (pg != null) pg.stop(); } catch (Exception ignored) {}
            throw new IllegalStateException("Failed to launch self-contained ATDD stack: " + e.getMessage(), e);
        }
    }

    public synchronized void shutdown() {
        log.info("[atdd-stack] shutting down ...");
        try { if (monolith != null) monolith.close(); } catch (Exception ignored) {}
        try { if (floci != null) floci.stop(); } catch (Exception ignored) {}
        try { if (postgres != null) postgres.stop(); } catch (Exception ignored) {}
        instance = null;
    }

    @Override public void close() { shutdown(); }

    public int monolithPort() { return monolithPort; }
    public String baseUrl()   { return "http://localhost:" + monolithPort; }

    private static int findFreePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private static boolean isPortInUse(int port) {
        try (ServerSocket s = new ServerSocket(port)) {
            s.setReuseAddress(true);
            return false;
        } catch (IOException e) {
            return true;
        }
    }
}
