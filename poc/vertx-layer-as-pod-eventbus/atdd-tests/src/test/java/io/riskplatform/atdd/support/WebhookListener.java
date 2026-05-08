package io.riskplatform.atdd.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lightweight HTTP server that listens for incoming webhook callbacks.
 *
 * <p>Usage in a .feature file:
 * <pre>
 *   * def WebhookListener = Java.type('io.riskplatform.atdd.support.WebhookListener')
 *   * def listener = new WebhookListener()
 *   * def listenerPort = listener.start()
 *   * def callbackUrl = 'http://' + webhookListenerHost + ':' + listenerPort + '/callback'
 *   # ... register callbackUrl with the service ...
 *   # ... trigger a DECLINE transaction ...
 *   * def payload = listener.awaitCallback(3000)
 *   * match payload.decision == 'DECLINE'
 *   * listener.stop()
 * </pre>
 */
public class WebhookListener {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final AtomicInteger PORT_BASE = new AtomicInteger(15000);

    private HttpServer server;
    private final BlockingQueue<Map<String, Object>> received = new ArrayBlockingQueue<>(16);

    /**
     * Starts the listener on a random available port (in the 15 000–15 999 range).
     *
     * @return the port the server is listening on
     * @throws IOException if the server cannot bind
     */
    @SuppressWarnings("unchecked")
    public int start() throws IOException {
        int port = PORT_BASE.getAndIncrement() % 1000 + 15000;
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/callback", exchange -> {
            try (InputStream body = exchange.getRequestBody()) {
                byte[] bytes = body.readAllBytes();
                Map<String, Object> payload = MAPPER.readValue(bytes, Map.class);
                received.offer(payload);
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.setExecutor(null);
        server.start();
        return port;
    }

    /**
     * Blocks until a callback is received or the timeout elapses.
     *
     * @param timeoutMs maximum wait in milliseconds
     * @return the parsed JSON payload
     * @throws AssertionError if no callback arrives within the timeout
     */
    public Map<String, Object> awaitCallback(long timeoutMs) {
        try {
            Map<String, Object> payload = received.poll(timeoutMs, TimeUnit.MILLISECONDS);
            if (payload == null) {
                throw new AssertionError(
                        "No webhook callback received within " + timeoutMs + " ms");
            }
            return payload;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for webhook callback", e);
        }
    }

    /**
     * Stops the HTTP server, freeing the port.
     */
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }
}
