package io.riskplatform.rules.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.riskplatform.rules.client.admin.AdminClient;
import io.riskplatform.rules.client.admin.RuleInfo;
import io.riskplatform.rules.client.config.ClientConfig;
import io.riskplatform.rules.client.config.Environment;
import io.riskplatform.rules.client.config.RetryPolicy;
import io.riskplatform.rules.client.http.JsonHttpClient;
import io.riskplatform.rules.client.sync.HealthStatus;
import io.riskplatform.rules.client.sync.SyncClient;
import io.riskplatform.rules.client.webhooks.Subscription;
import io.riskplatform.rules.client.webhooks.WebhooksClient;
import io.riskplatform.sdks.riskevents.RiskDecision;
import io.riskplatform.sdks.riskevents.RiskRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.File;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the Java Risk Client SDK.
 *
 * Brings up the full Vertx distributed stack via Docker Compose and exercises
 * every major SDK surface against a real running server.
 *
 * Run with: ./gradlew :sdks:risk-client-java:integrationTest
 *
 * The compose file is expected at
 * poc/vertx-layer-as-pod-eventbus/docker-compose.yml relative to the repo root.
 * The controller-app service must expose port 8080.
 */
@Tag("integration")
@Testcontainers
class RiskClientIntegrationTest {

    private static final String CONTROLLER_SERVICE = "controller-app";
    private static final int    CONTROLLER_PORT    = 8080;

    /**
     * Full application stack.  Wait strategy polls /healthz so tests only
     * start once the server is accepting traffic.
     */
    @Container
    @SuppressWarnings({"resource", "unchecked"})
    static final DockerComposeContainer<?> appStack =
            new DockerComposeContainer<>(
                    new File("../../poc/vertx-layer-as-pod-eventbus/docker-compose.yml"))
            .withExposedService(CONTROLLER_SERVICE, CONTROLLER_PORT,
                    Wait.forHttp("/healthz")
                        .forStatusCode(200)
                        .withStartupTimeout(Duration.ofMinutes(3)));

    private static SyncClient     sync;
    private static WebhooksClient webhooks;
    private static AdminClient    admin;

    @BeforeAll
    static void setup() {
        String host = appStack.getServiceHost(CONTROLLER_SERVICE, CONTROLLER_PORT);
        int    port = appStack.getServicePort(CONTROLLER_SERVICE, CONTROLLER_PORT);
        String baseUrl = "http://" + host + ":" + port;

        ClientConfig config = ClientConfig.builder()
                .environment(Environment.LOCAL)
                .apiKey("test")
                .timeout(Duration.ofSeconds(5))
                .retry(RetryPolicy.exponentialBackoff())
                .build();

        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // IntegrationJsonHttpClient wraps JsonHttpClient with a runtime base-URL
        // override so we can point at the Testcontainers-mapped port.
        JsonHttpClient http = new IntegrationJsonHttpClient(config, mapper, baseUrl);

        sync     = new SyncClient(config, http);
        webhooks = new WebhooksClient(config, http);
        admin    = new AdminClient(config, http);
    }

    // -----------------------------------------------------------------------
    // Functional — evaluate
    // -----------------------------------------------------------------------

    /** Low-value transaction from a known device must be approved. */
    @Test
    void evaluate_low_amount_returns_approve() {
        RiskDecision decision = sync.evaluate(request("tx-java-1", 1_00));
        assertThat(decision.decision()).isEqualTo("APPROVE");
    }

    /** Very high amount triggers a decline or review by the risk engine. */
    @Test
    void evaluate_high_amount_returns_decline_or_review() {
        RiskDecision decision = sync.evaluate(request("tx-java-2", 900_000_00));
        assertThat(decision.decision()).isIn("DECLINE", "REVIEW");
    }

    /** Unknown device raises the risk score regardless of amount. */
    @Test
    void evaluate_new_device_flag_does_not_approve_without_review() {
        RiskDecision decision = sync.evaluate(requestNewDevice("tx-java-3", 50_000_00));
        assertThat(decision.decision()).isIn("REVIEW", "DECLINE");
    }

    /** Batch endpoint returns one decision per request in the same order. */
    @Test
    void evaluate_batch_returns_decision_per_request() {
        List<RiskRequest> batch = List.of(
                request("tx-java-b1", 1_00),
                request("tx-java-b2", 2_00),
                request("tx-java-b3", 3_00));
        List<RiskDecision> decisions = sync.evaluateBatch(batch);
        assertThat(decisions).hasSize(3);
        decisions.forEach(d ->
                assertThat(d.decision()).isIn("APPROVE", "DECLINE", "REVIEW"));
    }

    /** Submitting the same transactionId twice must yield identical decisions. */
    @Test
    void idempotency_same_transaction_id_returns_same_decision() {
        String txId = "tx-java-idem-" + UUID.randomUUID();
        RiskDecision first  = sync.evaluate(request(txId, 1_00));
        RiskDecision second = sync.evaluate(request(txId, 1_00));
        assertThat(first.decision()).isEqualTo(second.decision());
        assertThat(first.reason()).isEqualTo(second.reason());
    }

    // -----------------------------------------------------------------------
    // Health
    // -----------------------------------------------------------------------

    @Test
    void health_endpoint_returns_up() {
        HealthStatus status = sync.health();
        assertThat(status.isUp()).isTrue();
        assertThat(status.status()).isEqualTo("UP");
    }

    // -----------------------------------------------------------------------
    // Webhooks
    // -----------------------------------------------------------------------

    @Test
    void webhook_subscribe_returns_populated_subscription() {
        Subscription sub = webhooks.subscribe("http://localhost:9999/cb", "DECLINE");
        assertThat(sub.id()).isNotBlank();
        assertThat(sub.callbackUrl()).isEqualTo("http://localhost:9999/cb");
        assertThat(sub.eventFilter()).isEqualTo("DECLINE");
    }

    @Test
    void webhook_list_includes_previously_registered_subscription() {
        String callbackUrl = "http://localhost:9997/cb-list-" + UUID.randomUUID();
        webhooks.subscribe(callbackUrl, "REVIEW");
        List<Subscription> list = webhooks.list();
        assertThat(list).isNotEmpty();
        boolean found = list.stream().anyMatch(s -> s.callbackUrl().equals(callbackUrl));
        assertThat(found).isTrue();
    }

    // -----------------------------------------------------------------------
    // Admin
    // -----------------------------------------------------------------------

    @Test
    void admin_list_rules_returns_at_least_one_enabled_rule() {
        List<RuleInfo> rules = admin.listRules();
        assertThat(rules).isNotEmpty();
        rules.forEach(r -> {
            assertThat(r.id()).isNotBlank();
            assertThat(r.name()).isNotBlank();
        });
        long enabledCount = rules.stream().filter(RuleInfo::enabled).count();
        assertThat(enabledCount).isGreaterThan(0);
    }

    @Test
    void admin_test_rule_returns_valid_decision() {
        RiskDecision decision = admin.testRule(request("tx-admin-1", 1_00));
        assertThat(decision.decision()).isIn("APPROVE", "DECLINE", "REVIEW");
        assertThat(decision.reason()).isNotBlank();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static RiskRequest request(String txId, long amountCents) {
        return new RiskRequest(
                txId, "cust-1", amountCents,
                "corr-" + txId, "idem-" + txId, false);
    }

    private static RiskRequest requestNewDevice(String txId, long amountCents) {
        return new RiskRequest(
                txId, "cust-new", amountCents,
                "corr-" + txId, "idem-" + txId + "-" + UUID.randomUUID(), true);
    }
}
