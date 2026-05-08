package io.riskplatform.rules.client.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.riskplatform.rules.client.RiskClientException;
import io.riskplatform.sdks.riskevents.RiskDecision;
import io.riskplatform.sdks.riskevents.RiskRequest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Bidirectional WebSocket channel. Open once, send many requests.
 * Thread-safe: send() and receive() can be called from different threads.
 */
public final class RiskChannel implements AutoCloseable {

    private final WebSocket ws;
    private final ObjectMapper mapper;
    private final LinkedBlockingQueue<RiskDecision> inbox = new LinkedBlockingQueue<>();

    RiskChannel(WebSocket ws, ObjectMapper mapper) {
        this.ws     = ws;
        this.mapper = mapper;
    }

    /** Send a request asynchronously; the reply will appear in the receive queue. */
    public CompletableFuture<Void> send(RiskRequest req) {
        try {
            String json = mapper.writeValueAsString(req);
            return ws.sendText(json, true).toCompletableFuture().thenApply(w -> null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Block until a decision is available or the timeout elapses.
     * Returns null on timeout.
     */
    public RiskDecision receive(long timeoutMs) {
        try {
            return inbox.poll(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    void enqueue(RiskDecision decision) {
        inbox.offer(decision);
    }

    @Override
    public void close() {
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "client close");
    }

    // -----------------------------------------------------------------------

    /** Listener wired to the underlying WebSocket. */
    static final class Listener implements WebSocket.Listener {

        private final RiskChannel channel;
        private final ObjectMapper mapper;
        private final StringBuilder buffer = new StringBuilder();

        Listener(RiskChannel channel, ObjectMapper mapper) {
            this.channel = channel;
            this.mapper  = mapper;
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String text = buffer.toString();
                buffer.setLength(0);
                try {
                    RiskDecision d = mapper.readValue(text, RiskDecision.class);
                    channel.enqueue(d);
                } catch (Exception ignored) {}
            }
            ws.request(1);
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            // silently discard; channel.receive() will time-out
        }
    }
}
