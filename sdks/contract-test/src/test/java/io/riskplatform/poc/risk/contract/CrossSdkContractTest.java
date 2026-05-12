package io.riskplatform.rules.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.riskplatform.rules.client.RiskClient;
import io.riskplatform.rules.client.config.ClientConfig;
import io.riskplatform.rules.client.config.Environment;
import io.riskplatform.rules.client.config.RetryPolicy;
import io.riskplatform.rules.client.http.JsonHttpClient;
import io.riskplatform.rules.client.sync.SyncClient;
import io.riskplatform.sdks.riskevents.RiskDecision;
import io.riskplatform.sdks.riskevents.RiskRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
 * Run with: RISK_BASE_URL=http://localhost:8080 ./gradlew :sdks:contract-test:test -Pcontract
 */
@Tag("contract")
class CrossSdkContractTest {

    private static final Duration SERVER_TIMEOUT = Duration.ofMinutes(3);

    private static SyncClient sync;
    private static String     serverUrl;

    @BeforeAll
    static void setup() throws Exception {
        serverUrl = System.getenv().getOrDefault("RISK_BASE_URL", "http://localhost:8080");
        waitForServer(serverUrl);

        ClientConfig config = ClientConfig.builder()
                .environment(Environment.LOCAL)
                .apiKey("test")
                .timeout(Duration.ofSeconds(10))
                .retry(RetryPolicy.exponentialBackoff())
                .build();

        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        sync = new SyncClient(config, new ContractJsonHttpClient(config, mapper, serverUrl));
    }


    private static void waitForServer(String baseUrl) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/ready"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        long deadlineNanos = System.nanoTime() + SERVER_TIMEOUT.toNanos();
        Exception lastError = null;
        while (System.nanoTime() < deadlineNanos) {
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return;
                }
                lastError = new IllegalStateException("Unexpected health status " + response.statusCode());
            } catch (Exception e) {
                lastError = e;
            }
            Thread.sleep(1_000);
        }
        throw new IllegalStateException(
                "Risk server did not become healthy at " + baseUrl +
                " within " + SERVER_TIMEOUT.toSeconds() + "s. " +
                "Start the compose stack with ./nx up vertx-layer-as-pod-eventbus or run via ./nx test all --with-infra-compose.",
                lastError);
    }

    // -----------------------------------------------------------------------
    // Contract tests
    // -----------------------------------------------------------------------

    /**
     * Low-value transaction: all three SDKs must agree on the engine outcome.
     */
    @Test
    void all_sdks_agree_on_low_amount_decision() throws Exception {
        String txId = "tx-cross-low-1";
        RiskRequest req = request(txId, 1_00);

        RiskDecision javaDecision = sync.evaluate(req);
        SdkResult    tsDecision   = invokeTsSdk(txId, 1_00);
        SdkResult    goDecision   = invokeGoSdk(txId, 1_00);

        assertThat(javaDecision.decision())
                .as("Java decision")
                .isIn("APPROVE", "DECLINE", "REVIEW");
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
        SdkResult    tsDecision   = invokeTsSdk(txId, 200_000_00, true);
        SdkResult    goDecision   = invokeGoSdk(txId, 200_000_00, true);

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
        SdkResult    tsDecision   = invokeTsSdk(txId, 50_00);
        SdkResult    goDecision   = invokeGoSdk(txId, 50_00);

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
    private SdkResult invokeTsSdk(String txId, long amountCents) throws Exception {
        return invokeTsSdk(txId, amountCents, false);
    }

    private SdkResult invokeTsSdk(String txId, long amountCents, boolean newDevice) throws Exception {
        return invokeScript("src/test/scripts/invoke_ts.sh",
                txId, String.valueOf(amountCents), String.valueOf(newDevice), serverUrl);
    }

    /**
     * Invokes the Go SDK via invoke_go.sh and parses the JSON result.
     */
    private SdkResult invokeGoSdk(String txId, long amountCents) throws Exception {
        return invokeGoSdk(txId, amountCents, false);
    }

    private SdkResult invokeGoSdk(String txId, long amountCents, boolean newDevice) throws Exception {
        return invokeScript("src/test/scripts/invoke_go.sh",
                txId, String.valueOf(amountCents), String.valueOf(newDevice), serverUrl);
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
