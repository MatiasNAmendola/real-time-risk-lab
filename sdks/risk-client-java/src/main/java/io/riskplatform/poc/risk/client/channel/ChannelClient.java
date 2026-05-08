package io.riskplatform.rules.client.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.riskplatform.rules.client.RiskClientException;
import io.riskplatform.rules.client.config.ClientConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;

/**
 * WebSocket channel factory. Each call to {@link #open()} creates a new
 * persistent connection.
 */
public final class ChannelClient {

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final String wsUrl;

    public ChannelClient(ClientConfig config, HttpClient httpClient, ObjectMapper mapper) {
        this.httpClient = httpClient;
        this.mapper     = mapper;
        String base = config.environment().restBaseUrl()
                .replace("https://", "wss://")
                .replace("http://",  "ws://");
        this.wsUrl = base + "/ws/risk";
    }

    /** Opens a bidirectional WebSocket channel. The caller must close it when done. */
    public RiskChannel open() {
        try {
            // placeholder channel — listener is wired after creation
            RiskChannel[] holder = new RiskChannel[1];
            WebSocket ws = httpClient.newWebSocketBuilder()
                    .header("X-API-Key", "sdk-client")
                    .connectTimeout(Duration.ofSeconds(5))
                    .buildAsync(URI.create(wsUrl), new WebSocket.Listener() {
                        @Override
                        public java.util.concurrent.CompletionStage<?> onText(
                                WebSocket ws, CharSequence data, boolean last) {
                            if (holder[0] != null) {
                                try {
                                    io.riskplatform.sdks.riskevents.RiskDecision d =
                                            mapper.readValue(data.toString(),
                                                    io.riskplatform.sdks.riskevents.RiskDecision.class);
                                    holder[0].enqueue(d);
                                } catch (Exception ignored) {}
                            }
                            ws.request(1);
                            return null;
                        }
                    })
                    .join();

            holder[0] = new RiskChannel(ws, mapper);
            return holder[0];
        } catch (Exception e) {
            throw new RiskClientException("WebSocket connect to " + wsUrl + " failed", e);
        }
    }
}
