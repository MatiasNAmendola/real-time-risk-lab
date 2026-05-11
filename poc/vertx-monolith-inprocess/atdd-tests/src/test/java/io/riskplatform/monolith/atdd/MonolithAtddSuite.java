package io.riskplatform.monolith.atdd;

import com.intuit.karate.junit5.Karate;
import io.riskplatform.monolith.atdd.support.MonolithStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

/**
 * Self-launching ATDD suite for the vertx-monolith-inprocess PoC.
 *
 * <p>Lifecycle approach (Option A — Testcontainers + in-test JVM fork): the suite owns
 * the full stack via {@link MonolithStack}. Postgres + Floci come up as containers,
 * the monolith fat-jar runs as a child JVM on a randomly chosen port, and Karate
 * features pick up that port via {@code -Dmonolith.baseUrl} (read by karate-config.js).
 *
 * <p>Rationale: dockerCompose Gradle plugin (Option B) would require adding a Compose
 * service for the monolith image; this in-test approach reuses the existing fat-jar
 * task and stays inside the testing toolchain. OrbStack-compatible env is set by
 * the build script.
 */
class MonolithAtddSuite {

    @BeforeAll
    static void startStack() {
        MonolithStack.start();
    }

    @AfterAll
    static void stopStack() {
        // Shutdown is also registered as a JVM hook, but stopping here releases
        // ports faster when running multiple suites back-to-back.
        MonolithStack stack = null;
        try {
            stack = MonolithStack.start(); // returns existing singleton
        } catch (Exception ignored) {}
        if (stack != null) stack.shutdown();
    }

    @Karate.Test
    Karate runAll() {
        return Karate.run("classpath:features").relativeTo(getClass());
    }
}
