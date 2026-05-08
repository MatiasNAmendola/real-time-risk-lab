package io.riskplatform.rules.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.riskplatform.rules.client.admin.AdminClient;
import io.riskplatform.rules.client.channel.ChannelClient;
import io.riskplatform.rules.client.config.ClientConfig;
import io.riskplatform.rules.client.config.Environment;
import io.riskplatform.rules.client.config.RetryPolicy;
import io.riskplatform.rules.client.events.EventsClient;
import io.riskplatform.rules.client.http.JsonHttpClient;
import io.riskplatform.rules.client.queue.QueueClient;
import io.riskplatform.rules.client.stream.StreamClient;
import io.riskplatform.rules.client.sync.SyncClient;
import io.riskplatform.rules.client.webhooks.WebhooksClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Entry point for the Risk Decision Platform Client SDK.
 *
 * <pre>{@code
 * RiskClient client = RiskClient.builder()
 *     .environment(Environment.LOCAL)
 *     .apiKey("test-key")
 *     .timeout(Duration.ofMillis(280))
 *     .retry(RetryPolicy.exponentialBackoff())
 *     .build();
 *
 * RiskDecision decision = client.sync().evaluate(req);
 * }</pre>
 */
public final class RiskClient {

    private final SyncClient     sync;
    private final StreamClient   stream;
    private final ChannelClient  channel;
    private final EventsClient   events;
    private final QueueClient    queue;
    private final WebhooksClient webhooks;
    private final AdminClient    admin;

    private RiskClient(ClientConfig config) {
        ObjectMapper mapper = buildMapper();
        JsonHttpClient jsonHttp = new JsonHttpClient(config, mapper);
        HttpClient rawHttp = jsonHttp.rawClient();
        SqsClient sqsClient = buildSqsClient();

        this.sync     = new SyncClient(config, jsonHttp);
        this.stream   = new StreamClient(config, jsonHttp, mapper);
        this.channel  = new ChannelClient(config, rawHttp, mapper);
        this.events   = new EventsClient(config, mapper);
        this.queue    = new QueueClient(config, sqsClient, mapper);
        this.webhooks = new WebhooksClient(config, jsonHttp);
        this.admin    = new AdminClient(config, jsonHttp);
    }

    public SyncClient     sync()     { return sync; }
    public StreamClient   stream()   { return stream; }
    public ChannelClient  channel()  { return channel; }
    public EventsClient   events()   { return events; }
    public QueueClient    queue()    { return queue; }
    public WebhooksClient webhooks() { return webhooks; }
    public AdminClient    admin()    { return admin; }

    public static Builder builder() { return new Builder(); }

    // -----------------------------------------------------------------------

    private static ObjectMapper buildMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    private static SqsClient buildSqsClient() {
        return SqsClient.builder()
                .region(Region.US_EAST_1)
                .build();
    }

    // -----------------------------------------------------------------------

    public static final class Builder {
        private Environment  environment;
        private String       apiKey;
        private Duration     timeout;
        private RetryPolicy  retryPolicy;
        private String       otlpEndpoint;

        public Builder environment(Environment e) { this.environment = e;    return this; }
        public Builder apiKey(String k)           { this.apiKey = k;         return this; }
        public Builder timeout(Duration d)        { this.timeout = d;        return this; }
        public Builder retry(RetryPolicy p)       { this.retryPolicy = p;    return this; }
        public Builder otlp(String endpoint)      { this.otlpEndpoint = endpoint; return this; }

        public RiskClient build() {
            ClientConfig config = ClientConfig.builder()
                    .environment(environment)
                    .apiKey(apiKey)
                    .timeout(timeout)
                    .retry(retryPolicy != null ? retryPolicy : RetryPolicy.exponentialBackoff())
                    .otlp(otlpEndpoint)
                    .build();
            return new RiskClient(config);
        }
    }
}
