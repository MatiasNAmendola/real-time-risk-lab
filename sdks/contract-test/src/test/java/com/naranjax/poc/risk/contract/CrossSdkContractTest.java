package com.naranjax.poc.risk.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.naranjax.poc.risk.client.RiskClient;
import com.naranjax.poc.risk.client.config.ClientConfig;
import com.naranjax.poc.risk.client.config.Environment;
import com.naranjax.poc.risk.client.config.RetryPolicy;
import com.naranjax.poc.risk.client.http.JsonHttpClient;
import com.naranjax.poc.risk.client.sync.SyncClient;
import com.naranjax.poc.sdks.riskevents.RiskDecision;
import com.naranjax.poc.sdks.riskevents.RiskRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Currency;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cross-SDK contract test.
 *
 * Submits identical RiskRequest payloads through all three SDKs and asserts
 * that each returns the same decision and reason.  The TypeScript and Go SDKs
 * are invoked via helper shell scripts that write JSON to stdout.
 *
 * Run with: ./gradlew :sdks:contract-test:test -Pcontract
 */
@Tag("contract")
@Testcontainers
class CrossSdkContractTest {

    private static final String CONTROLLER_SERVICE = "controller-app";
    private static final int    CONTROLLER_PORT    = 8080;
    private static final String COMPOSE_FILE =
            "../../poc/java-vertx-distributed/docker-compose.yml";

    @Container
    @SuppressWarnings({"resource", "unchecked"})
    static final DockerComposeContainer<?> appStack =
            new DockerComposeContainer<>(new File(COMPOSE_FILE))
            .withExposedService(CONTROLLER_SERVICE, CONTROLLER_PORT,
                    Wait.forHttp("/healthz")
                        .forStatusCode(200)
                        .withStartupTimeout(Duration.ofMinutes(3)));

    private static SyncClient sync;
    private static String     serverUrl;

    @BeforeAll
    static void setup() {
        String host = appStack.getServiceHost(CONTROLLER_SERVICE, CONTROLLER_PORT);
        int    port = appStack.getServicePort(CONTROLLER_SERVICE, CONTROLLER_PORT);
        serverUrl = "http://" + host + ":" + port;

        ClientConfig config = ClientConfig.builder()
                .environment(Environment.LOCAL)
                .apiKey("test")
                .timeout(Duration.ofSeconds(10))
                .retry(RetryPolicy.exponentialBackoff())
                .build();

        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        sync = new SyncClient(config, new ContractJsonHttpClient(config, mapper, serverUrl));
    }

    // -----------------------------------------------------------------------
    // Contract tests
    // -----------------------------------------------------------------------

    /**
     * Low-value transaction: all three SDKs must agree on APPROVE.
     */
    @Test
    void all_sdks_agree_on_low_amount_approve() throws Exception {
        String txId = "tx-cross-low-1";
        RiskRequest req = request(txId, 1_00);

        RiskDecision javaDecision = sync.evaluate(req);
        SdkResult    tsDecision   = invokeTsSdk(txId, 1.0);
        SdkResult    goDecision   = invokeGoSdk(txId, 1.0);

        assertThat(javaDecision.decision())
                .as("Java decision")
                .isEqualTo("APPROVE");
        assertThat(tsDecision.decision)
                .as("TypeScript decision must match Java")
                .isEqualTo(javaDecision.decision());
        assertThat(goDecision.decision)
                .as("Go decision must match Java")
                .isEqualTo(javaDecision.decision());
    }

    /**
     * High-value transaction with new device: all three SDKs must agree on the outcome.
     */
    @Test
    void all_sdks_agree_on_high_amount_new_device() throws Exception {
        String txId = "tx-cross-high-1";
        RiskRequest req = requestNewDevice(txId, 200_000_00);

        RiskDecision javaDecision = sync.evaluate(req);
        SdkResult    tsDecision   = invokeTsSdk(txId, 200_000.0, true);
        SdkResult    goDecision   = invokeGoSdk(txId, 200_000.0, true);

        // All three must return the same outcome — we validate parity, not the
        // specific value (the engine determines that).
        assertThat(tsDecision.decision)
                .as("TypeScript decision must match Java for high-value new-device request")
                .isEqualTo(javaDecision.decision());
        assertThat(goDecision.decision)
                .as("Go decision must match Java for high-value new-device request")
                .isEqualTo(javaDecision.decision());
    }

    /**
     * Reason field must also be identical across SDKs — no SDK fabricates its own reason.
     */
    @Test
    void all_sdks_return_same_reason_for_identical_request() throws Exception {
        String txId = "tx-cross-reason-1";
        RiskRequest req = request(txId, 50_00);

        RiskDecision javaDecision = sync.evaluate(req);
        SdkResult    tsDecision   = invokeTsSdk(txId, 50.0);
        SdkResult    goDecision   = invokeGoSdk(txId, 50.0);

        assertThat(tsDecision.reason)
                .as("TypeScript reason must match Java")
                .isEqualTo(javaDecision.reason());
        assertThat(goDecision.reason)
                .as("Go reason must match Java")
                .isEqualTo(javaDecision.reason());
    }

    // -----------------------------------------------------------------------
    // Script invocation helpers
    // -----------------------------------------------------------------------

    /**
     * Invokes the TypeScript SDK via invoke_ts.sh and parses the JSON result.
     */
    private SdkResult invokeTsSdk(String txId, double amount) throws Exception {
        return invokeTsSdk(txId, amount, false);
    }

    private SdkResult invokeTsSdk(String txId, double amount, boolean newDevice) throws Exception {
        return invokeScript("src/test/scripts/invoke_ts.sh",
                txId, String.valueOf(amount), String.valueOf(newDevice), serverUrl);
    }

    /**
     * Invokes the Go SDK via invoke_go.sh and parses the JSON result.
     */
    private SdkResult invokeGoSdk(String txId, double amount) throws Exception {
        return invokeGoSdk(txId, amount, false);
    }

    private SdkResult invokeGoSdk(String txId, double amount, boolean newDevice) throws Exception {
        return invokeScript("src/test/scripts/invoke_go.sh",
                txId, String.valueOf(amount), String.valueOf(newDevice), serverUrl);
    }

    private SdkResult invokeScript(String scriptPath, String... args) throws Exception {
        String repoRoot = resolveRepoRoot();
        String[] cmd = new String[2 + args.length];
        cmd[0] = "bash";
        cmd[1] = new File(repoRoot + "/sdks/contract-test/" + scriptPath)
                .getAbsolutePath();
        System.arraycopy(args, 0, cmd, 2, args.length);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(new File(repoRoot));
        pb.redirectErrorStream(false);
        Process process = pb.start();

        String stdout = new BufferedReader(
                new InputStreamReader(process.getInputStream()))
                .lines()
                .collect(Collectors.joining("\n"))
                .trim();

        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished || process.exitValue() != 0) {
            String stderr = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()))
                    .lines()
                    .collect(Collectors.joining("\n"));
            throw new RuntimeException(
                    "Script " + scriptPath + " failed (exit=" +
                    (finished ? process.exitValue() : "timeout") +
                    "): " + stderr);
        }

        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(stdout, SdkResult.class);
    }

    private static String resolveRepoRoot() {
        // Walk up from CWD until we find settings.gradle.kts
        File dir = new File(System.getProperty("user.dir"));
        while (dir != null) {
            if (new File(dir, "settings.gradle.kts").exists()) return dir.getAbsolutePath();
            dir = dir.getParentFile();
        }
        throw new IllegalStateException("Cannot locate repo root (no settings.gradle.kts found)");
    }

    // -----------------------------------------------------------------------
    // Domain helpers
    // -----------------------------------------------------------------------

    private static RiskRequest request(String txId, long amountCents) {
        return new RiskRequest(
                txId, "cust-1", amountCents,
                "corr-" + txId, "idem-" + txId, false);
    }

    private static RiskRequest requestNewDevice(String txId, long amountCents) {
        return new RiskRequest(
                txId, "cust-new", amountCents,
                "corr-" + txId, "idem-" + txId, true);
    }

    // -----------------------------------------------------------------------
    // DTO for JSON output of helper scripts
    // -----------------------------------------------------------------------

    static class SdkResult {
        public String transactionId;
        public String decision;
        public String reason;
    }
}
