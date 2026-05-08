package io.riskplatform.rules.client.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.riskplatform.rules.client.RiskClientException;
import io.riskplatform.rules.client.config.ClientConfig;
import io.riskplatform.rules.client.http.JsonHttpClient;
import io.riskplatform.sdks.riskevents.DecisionEvent;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.Iterator;

/**
 * SSE channel: streams DecisionEvents from the server push endpoint.
 * The returned Stream is lazy; open the connection only when the stream is consumed.
 */
public final class StreamClient {

    private final JsonHttpClient http;
    private final ObjectMapper mapper;
    private final String sseUrl;

    public StreamClient(ClientConfig config, JsonHttpClient http, ObjectMapper mapper) {
        this.http   = http;
        this.mapper = mapper;
        this.sseUrl = config.environment().restBaseUrl() + "/risk/stream";
    }

    /**
     * Opens the SSE stream and returns a lazy {@link Stream} of {@link DecisionEvent}.
     * The caller must close the stream when done to release the underlying connection.
     */
    public Stream<DecisionEvent> decisions() {
        InputStream is = http.openSseStream(sseUrl);
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8));

        Iterator<DecisionEvent> iterator = new SseIterator(reader, mapper);
        return StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), false)
                .onClose(() -> {
                    try { reader.close(); } catch (Exception ignored) {}
                });
    }

    // -----------------------------------------------------------------------

    private static final class SseIterator implements Iterator<DecisionEvent> {

        private final BufferedReader reader;
        private final ObjectMapper mapper;
        private DecisionEvent next;
        private boolean done;

        SseIterator(BufferedReader reader, ObjectMapper mapper) {
            this.reader = reader;
            this.mapper = mapper;
            advance();
        }

        private void advance() {
            if (done) return;
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data:")) {
                        String json = line.substring(5).strip();
                        next = mapper.readValue(json, DecisionEvent.class);
                        return;
                    }
                }
            } catch (Exception e) {
                // stream closed or error
            }
            done = true;
            next = null;
        }

        @Override public boolean hasNext() { return next != null; }

        @Override public DecisionEvent next() {
            DecisionEvent current = next;
            advance();
            return current;
        }
    }
}
