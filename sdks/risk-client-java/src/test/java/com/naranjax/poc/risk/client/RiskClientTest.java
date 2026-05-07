package com.naranjax.poc.risk.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.naranjax.poc.risk.client.admin.AdminClient;
import com.naranjax.poc.risk.client.admin.RuleInfo;
import com.naranjax.poc.risk.client.config.ClientConfig;
import com.naranjax.poc.risk.client.config.Environment;
import com.naranjax.poc.risk.client.config.RetryPolicy;
import com.naranjax.poc.risk.client.http.JsonHttpClient;
import com.naranjax.poc.risk.client.queue.QueueClient;
import com.naranjax.poc.risk.client.sync.HealthStatus;
import com.naranjax.poc.risk.client.sync.SyncClient;
import com.naranjax.poc.risk.client.webhooks.Subscription;
import com.naranjax.poc.risk.client.webhooks.WebhooksClient;
import com.naranjax.poc.sdks.riskevents.RiskDecision;
import com.naranjax.poc.sdks.riskevents.RiskRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RiskClientTest {

    @Mock private JsonHttpClient mockHttp;
    @Mock private SqsClient mockSqs;

    private ObjectMapper mapper;
    private ClientConfig config;
    private RiskRequest sampleRequest;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        config = ClientConfig.builder()
                .environment(Environment.LOCAL)
                .apiKey("test-key")
                .timeout(Duration.ofMillis(280))
                .retry(RetryPolicy.exponentialBackoff())
                .build();

        sampleRequest = new RiskRequest(
                "txn-001", "cust-001", 1_000L,
                "corr-001", "idem-txn-001", false);
    }

    // --- sync.evaluate ---

    @Test
    void sync_evaluate_returns_decision() {
        RiskDecision expected = new RiskDecision("txn-001", "APPROVE", "low risk", Duration.ofMillis(12));
        when(mockHttp.postJson(contains("/risk"), any(), eq(RiskDecision.class)))
                .thenReturn(expected);

        SyncClient sync = new SyncClient(config, mockHttp);
        RiskDecision result = sync.evaluate(sampleRequest);

        assertThat(result.decision()).isEqualTo("APPROVE");
        assertThat(result.isApproved()).isTrue();
    }

    @Test
    void sync_evaluate_returns_decline() {
        RiskDecision expected = new RiskDecision("txn-002", "DECLINE", "high amount", Duration.ofMillis(8));
        when(mockHttp.postJson(contains("/risk"), any(), eq(RiskDecision.class)))
                .thenReturn(expected);

        SyncClient sync = new SyncClient(config, mockHttp);
        RiskDecision result = sync.evaluate(sampleRequest);

        assertThat(result.isDeclined()).isTrue();
    }

    @Test
    void sync_evaluateBatch_returns_list() {
        RiskDecision[] batch = {
            new RiskDecision("txn-001", "APPROVE", "ok", Duration.ofMillis(5)),
            new RiskDecision("txn-002", "REVIEW",  "manual check", Duration.ofMillis(7))
        };
        when(mockHttp.postJson(contains("/risk/batch"), any(), eq(RiskDecision[].class)))
                .thenReturn(batch);

        SyncClient sync = new SyncClient(config, mockHttp);
        List<RiskDecision> results = sync.evaluateBatch(List.of(sampleRequest, sampleRequest));

        assertThat(results).hasSize(2);
        assertThat(results.get(1).requiresReview()).isTrue();
    }

    @Test
    void sync_health_returns_status() {
        HealthStatus up = new HealthStatus("UP", "1.0.0");
        when(mockHttp.getJson(contains("/healthz"), eq(HealthStatus.class)))
                .thenReturn(up);

        SyncClient sync = new SyncClient(config, mockHttp);
        HealthStatus status = sync.health();

        assertThat(status.isUp()).isTrue();
        assertThat(status.version()).isEqualTo("1.0.0");
    }

    // --- retry behaviour ---

    @Test
    void sync_evaluate_propagates_exception_on_client_error() {
        when(mockHttp.postJson(any(), any(), any()))
                .thenThrow(new RiskClientException("HTTP 503", 503));

        SyncClient sync = new SyncClient(config, mockHttp);

        assertThatThrownBy(() -> sync.evaluate(sampleRequest))
                .isInstanceOf(RiskClientException.class)
                .hasMessageContaining("503");
    }

    // --- webhooks ---

    @Test
    void webhooks_subscribe_sends_correct_payload() {
        Subscription sub = new Subscription("sub-1", "http://cb/hook", "DECLINE", Instant.now());
        when(mockHttp.postJson(contains("/webhook/register"), any(), eq(Subscription.class)))
                .thenReturn(sub);

        WebhooksClient webhooks = new WebhooksClient(config, mockHttp);
        Subscription result = webhooks.subscribe("http://cb/hook", "DECLINE");

        assertThat(result.id()).isEqualTo("sub-1");
        assertThat(result.callbackUrl()).isEqualTo("http://cb/hook");
    }

    @Test
    void webhooks_list_returns_subscriptions() {
        Subscription[] subs = {
            new Subscription("s1", "http://a/cb", "DECLINE", Instant.now()),
            new Subscription("s2", "http://b/cb", "REVIEW",  Instant.now())
        };
        when(mockHttp.getJson(contains("/webhook/subscriptions"), eq(Subscription[].class)))
                .thenReturn(subs);

        WebhooksClient webhooks = new WebhooksClient(config, mockHttp);
        List<Subscription> list = webhooks.list();

        assertThat(list).hasSize(2);
    }

    @Test
    void webhooks_verify_accepts_valid_hmac() {
        WebhooksClient webhooks = new WebhooksClient(config, mockHttp);
        byte[] payload = "{\"decision\":\"DECLINE\"}".getBytes();
        String secret  = "test-secret";

        // compute expected signature
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            String sig = java.util.HexFormat.of().formatHex(mac.doFinal(payload));
            assertThat(webhooks.verify(payload, sig, secret)).isTrue();
        } catch (Exception e) {
            throw new AssertionError("HMAC computation failed", e);
        }
    }

    @Test
    void webhooks_verify_rejects_tampered_signature() {
        WebhooksClient webhooks = new WebhooksClient(config, mockHttp);
        boolean result = webhooks.verify(
                "{\"decision\":\"APPROVE\"}".getBytes(), "badhash", "secret");
        assertThat(result).isFalse();
    }

    // --- admin ---

    @Test
    void admin_listRules_returns_rules() {
        RuleInfo[] rules = {
            new RuleInfo("r1", "high-amount", "blocks >100k", true, 1),
            new RuleInfo("r2", "velocity",    "rate limit",   true, 2)
        };
        when(mockHttp.getJson(contains("/admin/rules"), eq(RuleInfo[].class)))
                .thenReturn(rules);

        AdminClient admin = new AdminClient(config, mockHttp);
        List<RuleInfo> list = admin.listRules();

        assertThat(list).hasSize(2);
        assertThat(list.get(0).name()).isEqualTo("high-amount");
    }

    @Test
    void admin_testRule_returns_decision() {
        RiskDecision expected = new RiskDecision("txn-001", "DECLINE", "rule match", Duration.ofMillis(3));
        when(mockHttp.postJson(contains("/admin/rules/test"), any(), eq(RiskDecision.class)))
                .thenReturn(expected);

        AdminClient admin = new AdminClient(config, mockHttp);
        RiskDecision result = admin.testRule(sampleRequest);

        assertThat(result.isDeclined()).isTrue();
    }

    // --- queue ---

    @Test
    void queue_sendDecisionRequest_invokes_sqs() throws Exception {
        when(mockSqs.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(SendMessageResponse.builder().messageId("msg-1").build());

        QueueClient queue = new QueueClient(config, mockSqs, mapper);
        queue.sendDecisionRequest(sampleRequest);

        verify(mockSqs, times(1)).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void queue_receiveDecisions_processes_message() throws Exception {
        // Use raw JSON that maps cleanly to RiskDecision
        String body = "{\"transactionId\":\"txn-001\",\"decision\":\"APPROVE\",\"reason\":\"ok\",\"elapsed\":\"PT0.005S\"}";

        Message msg = Message.builder()
                .body(body)
                .receiptHandle("rh-1")
                .build();

        when(mockSqs.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(msg).build());
        when(mockSqs.deleteMessage(any(software.amazon.awssdk.services.sqs.model.DeleteMessageRequest.class)))
                .thenReturn(software.amazon.awssdk.services.sqs.model.DeleteMessageResponse.builder().build());

        QueueClient queue = new QueueClient(config, mockSqs, mapper);
        int[] count = {0};
        int processed = queue.receiveDecisions(d -> count[0]++);

        assertThat(processed).isEqualTo(1);
        assertThat(count[0]).isEqualTo(1);
    }

    // --- environment ---

    @Test
    void environment_local_resolves_localhost_url() {
        assertThat(Environment.LOCAL.restBaseUrl()).startsWith("http://localhost");
        assertThat(Environment.LOCAL.kafkaBroker()).contains("localhost");
    }

    @Test
    void environment_prod_resolves_production_url() {
        assertThat(Environment.PROD.restBaseUrl()).startsWith("https://risk.naranjax.com");
    }

    // --- RetryPolicy ---

    @Test
    void retryPolicy_exponential_has_three_attempts() {
        RetryPolicy policy = RetryPolicy.exponentialBackoff();
        assertThat(policy.maxAttempts()).isEqualTo(3);
        assertThat(policy.multiplier()).isEqualTo(2.0);
    }
}
