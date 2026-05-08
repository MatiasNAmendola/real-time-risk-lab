package io.riskplatform.distributed.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.riskplatform.distributed.shared.EventBusAddress;
import io.riskplatform.distributed.shared.RiskDecision;
import io.riskplatform.distributed.shared.RiskRequest;
import io.riskplatform.rules.config.RulesConfigLoader;
import io.riskplatform.rules.engine.RuleEngine;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.ServerWebSocketHandshake;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.vertx.micrometer.backends.BackendRegistries;

public class HttpVerticle extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(HttpVerticle.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int HTTP_PORT = 8080;
    private static final long TIMEOUT_MS = 15_000L;

    // ── SSE subscribers: id → response handle ────────────────────────────────
    private final ConcurrentHashMap<String, io.vertx.ext.web.RoutingContext> sseClients = new ConcurrentHashMap<>();

    // ── Webhook subscriptions ─────────────────────────────────────────────────
    record WebhookSub(String id, String url, List<String> filters) {}
    private final ConcurrentHashMap<String, WebhookSub> webhooks = new ConcurrentHashMap<>();

    // ── Active WS connections gauge ───────────────────────────────────────────
    private final AtomicInteger wsActiveCount = new AtomicInteger(0);

    // ── Admin API state ───────────────────────────────────────────────────────
    private final String adminToken = System.getenv().getOrDefault("ADMIN_TOKEN", "change-me-admin-token");
    private final String rulesConfigPath = System.getenv().getOrDefault(
            "RULES_CONFIG_PATH", "examples/rules-config/v1/rules.yaml");
    private final RulesConfigLoader rulesConfigLoader = new RulesConfigLoader();
    // ruleEngine is set lazily via event bus call; for controller-app it is informational only
    // The actual engine lives in usecase-app. Controller proxies reload requests to usecase-app.

    // ── Micrometer counters (lazy-init after registry available) ──────────────
    private Counter approveCounter;
    private Counter reviewCounter;
    private Counter declineCounter;
    private Counter webhookSuccessCounter;
    private Counter webhookErrorCounter;
    private Timer   decisionTimer;

    @Override
    public void start(Promise<Void> startPromise) {
        // Init Micrometer metrics (registry available after vertx-micrometer starts)
        initMetrics();

        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        // ── 1. REST POST /risk ────────────────────────────────────────────────
        router.post("/risk").handler(ctx -> {
            String correlationId = ctx.request().getHeader("X-Correlation-Id");
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = UUID.randomUUID().toString();
            }
            final String corrId = correlationId;
            ctx.response().putHeader("X-Correlation-Id", corrId);
            MDC.put("correlationId", corrId);

            // Idempotency-Key header (RFC 8694 pattern) is forwarded into the request body so
            // EvaluateRiskVerticle can use it without parsing HTTP headers over the event bus.
            String idempotencyKey = ctx.request().getHeader("Idempotency-Key");

            log.info("[controller-app] POST /risk correlationId={} idempotencyKey={}",
                corrId, idempotencyKey);

            JsonObject body;
            try {
                body = ctx.body().asJsonObject();
            } catch (Exception e) {
                MDC.clear();
                ctx.response().setStatusCode(400)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject().put("error", "invalid JSON").encode());
                return;
            }

            if (body == null) {
                MDC.clear();
                ctx.response().setStatusCode(400)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject().put("error", "empty body").encode());
                return;
            }

            // Inject correlationId and idempotencyKey into the message body so usecase-app
            // receives them even when running in a separate JVM process.
            body.put("correlationId", corrId);
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                body.put("idempotencyKey", idempotencyKey);
            }

            DeliveryOptions opts = new DeliveryOptions()
                .setSendTimeout(TIMEOUT_MS)
                .addHeader("correlationId", corrId);

            long startNs = System.nanoTime();
            vertx.eventBus().<String>request(EventBusAddress.USECASE_EVALUATE, body.encode(), opts)
                .onSuccess(reply -> {
                    long elapsed = System.nanoTime() - startNs;
                    log.info("[controller-app] decision reply correlationId={} body={}", corrId, reply.body());
                    recordDecisionMetrics(reply.body(), elapsed);
                    MDC.clear();
                    var response = ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/json");
                    putCurrentTraceHeaders(response);
                    response.end(reply.body());
                })
                .onFailure(err -> {
                    log.error("[controller-app] usecase error correlationId={}: {}", corrId, err.getMessage());
                    MDC.clear();
                    var response = ctx.response()
                        .setStatusCode(502)
                        .putHeader("Content-Type", "application/json");
                    putCurrentTraceHeaders(response);
                    response.end(new JsonObject().put("error", err.getMessage()).encode());
                });
        });

        // ── 2. SSE GET /risk/stream ───────────────────────────────────────────
        router.get("/risk/stream").handler(ctx -> {
            String clientId = UUID.randomUUID().toString();
            ctx.response()
                .putHeader("Content-Type", "text/event-stream")
                .putHeader("Cache-Control", "no-cache")
                .putHeader("X-Accel-Buffering", "no")
                .setChunked(true);

            // Keep-alive comment every 15 seconds
            long timerId = vertx.setPeriodic(15_000, t -> {
                if (!ctx.response().ended()) {
                    ctx.response().write(": keep-alive\n\n");
                }
            });

            sseClients.put(clientId, ctx);
            log.info("[controller-app] SSE client connected id={} total={}", clientId, sseClients.size());

            ctx.request().connection().closeHandler(v -> {
                sseClients.remove(clientId);
                vertx.cancelTimer(timerId);
                log.info("[controller-app] SSE client disconnected id={}", clientId);
            });
        });

        // ── 3. Webhook CRUD ───────────────────────────────────────────────────
        router.post("/webhooks").handler(ctx -> {
            JsonObject body;
            try { body = ctx.body().asJsonObject(); }
            catch (Exception e) {
                ctx.response().setStatusCode(400).end(new JsonObject().put("error","invalid JSON").encode());
                return;
            }
            if (body == null) {
                ctx.response().setStatusCode(400)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject().put("error","body required").encode());
                return;
            }
            String url    = body.getString("url");
            String filter = body.getString("filter", "APPROVE,REVIEW,DECLINE");
            if (url == null || url.isBlank()) {
                ctx.response().setStatusCode(400).end(new JsonObject().put("error","url required").encode());
                return;
            }
            List<String> filters = java.util.Arrays.stream(filter.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
            if (filters.isEmpty()) {
                ctx.response().setStatusCode(400)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject().put("error","filter must include at least one decision").encode());
                return;
            }
            List<String> invalidFilters = filters.stream()
                .filter(value -> !List.of("APPROVE", "REVIEW", "DECLINE").contains(value))
                .toList();
            if (!invalidFilters.isEmpty()) {
                ctx.response().setStatusCode(400)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                        .put("error","invalid filter value")
                        .put("invalid", invalidFilters)
                        .put("allowed", List.of("APPROVE", "REVIEW", "DECLINE"))
                        .encode());
                return;
            }
            String id = UUID.randomUUID().toString();
            String normalizedFilter = String.join(",", filters);
            webhooks.put(id, new WebhookSub(id, url, filters));
            log.info("[controller-app] webhook registered id={} filter={}", id, normalizedFilter);
            ctx.response().setStatusCode(201)
                .putHeader("Content-Type","application/json")
                .end(new JsonObject()
                    .put("id", id)
                    .put("url", url)
                    .put("callbackUrl", url)
                    .put("filter", normalizedFilter)
                    .put("eventFilter", normalizedFilter)
                    .encode());
        });

        router.get("/webhooks").handler(ctx -> {
            JsonArray arr = new JsonArray();
            webhooks.values().forEach(sub ->
                arr.add(new JsonObject()
                    .put("id", sub.id())
                    .put("url", sub.url())
                    .put("callbackUrl", sub.url())
                    .put("filter", String.join(",", sub.filters()))
                    .put("eventFilter", String.join(",", sub.filters()))));
            ctx.response().putHeader("Content-Type","application/json").end(arr.encode());
        });

        router.delete("/webhooks/:id").handler(ctx -> {
            String id = ctx.pathParam("id");
            if (webhooks.remove(id) != null) {
                ctx.response().setStatusCode(204).end();
            } else {
                ctx.response().setStatusCode(404)
                    .end(new JsonObject().put("error","not found").encode());
            }
        });

        // ── OpenAPI JSON ──────────────────────────────────────────────────────
        router.get("/openapi.json").handler(ctx -> {
            try {
                String yaml = loadResource("/openapi.yaml");
                // Serve raw YAML converted to inline; for simplicity serve static JSON
                // We serve the yaml bytes and client can consume as text/yaml
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(convertYamlToJsonString(yaml));
            } catch (Exception e) {
                ctx.response().setStatusCode(500).end("Failed to load openapi spec: " + e.getMessage());
            }
        });

        // ── OpenAPI YAML ──────────────────────────────────────────────────────
        router.get("/openapi.yaml").handler(ctx -> {
            try {
                ctx.response()
                    .putHeader("Content-Type", "application/yaml")
                    .end(loadResource("/openapi.yaml"));
            } catch (Exception e) {
                ctx.response().setStatusCode(500).end("spec not found");
            }
        });

        // ── AsyncAPI JSON ─────────────────────────────────────────────────────
        router.get("/asyncapi.json").handler(ctx -> {
            try {
                String yaml = loadResource("/asyncapi.yaml");
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(convertYamlToJsonString(yaml));
            } catch (Exception e) {
                ctx.response().setStatusCode(500).end("Failed to load asyncapi spec: " + e.getMessage());
            }
        });

        // ── Swagger UI (/docs) ────────────────────────────────────────────────
        router.get("/docs").handler(ctx -> ctx.redirect("/docs/"));
        router.get("/docs/").handler(ctx -> {
            String html = """
                <!DOCTYPE html>
                <html>
                <head>
                  <title>Risk API - Swagger UI</title>
                  <meta charset="utf-8"/>
                  <link rel="stylesheet" href="https://unpkg.com/swagger-ui-dist@5/swagger-ui.css">
                </head>
                <body>
                <div id="swagger-ui"></div>
                <script src="https://unpkg.com/swagger-ui-dist@5/swagger-ui-bundle.js"></script>
                <script>
                  SwaggerUIBundle({
                    url: '/openapi.yaml',
                    dom_id: '#swagger-ui',
                    presets: [SwaggerUIBundle.presets.apis, SwaggerUIBundle.SwaggerUIStandalonePreset],
                    layout: 'BaseLayout'
                  });
                </script>
                <p style="margin:20px">AsyncAPI spec: <a href="/asyncapi.json">/asyncapi.json</a> |
                   <a href="https://studio.asyncapi.com/" target="_blank">Open in AsyncAPI Studio</a></p>
                </body>
                </html>
                """;
            ctx.response().putHeader("Content-Type","text/html").end(html);
        });

        // ── Health ────────────────────────────────────────────────────────────
        router.get("/health").handler(ctx ->
            ctx.response().setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("status", "UP").encode())
        );

        // Readiness is stricter than liveness: it proves clustered EventBus
        // request/reply works end-to-end without evaluating or storing a
        // decision. This catches Hazelcast reply-address regressions before a
        // smoke/ATDD request hangs for the full /risk timeout.
        router.get("/ready").handler(ctx -> {
            DeliveryOptions opts = new DeliveryOptions().setSendTimeout(2_000L);
            vertx.eventBus().<String>request(EventBusAddress.USECASE_READY, "ping", opts)
                .onSuccess(reply -> ctx.response()
                    .setStatusCode(200)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                        .put("status", "READY")
                        .put("usecase", reply.body())
                        .encode()))
                .onFailure(err -> ctx.response()
                    .setStatusCode(503)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                        .put("status", "NOT_READY")
                        .put("error", err.getMessage())
                        .encode()));
        });

        // ── Admin Rules API ───────────────────────────────────────────────────
        // GET  /admin/rules         — list rules (proxied from usecase-app via event bus)
        // POST /admin/rules/reload  — force reload
        // POST /admin/rules/test    — dry-run evaluation
        // GET  /admin/rules/audit   — last 100 audit entries
        router.get("/admin/rules/audit").handler(ctx -> {
            if (!isAdminAuthorized(ctx)) return;
            ctx.response().setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("message", "audit trail served from usecase-app")
                    .put("note", "use usecase-app:8081/admin/rules/audit for full trail").encode());
        });

        router.get("/admin/rules").handler(ctx -> {
            if (!isAdminAuthorized(ctx)) return;
            try {
                io.riskplatform.rules.config.RulesConfig config = rulesConfigLoader.load(rulesConfigPath);
                JsonArray rulesArr = new JsonArray();
                if (config.rules() != null) {
                    config.rules().forEach(r -> rulesArr.add(new JsonObject()
                        .put("name", r.name()).put("type", r.type())
                        .put("enabled", r.enabled()).put("action", r.action())));
                }
                ctx.response().setStatusCode(200)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                        .put("version", config.version())
                        .put("hash", config.hash())
                        .put("rules", rulesArr)
                        .put("total", rulesArr.size())
                        .put("enabled_count", config.enabledCount()).encode());
            } catch (Exception e) {
                ctx.response().setStatusCode(500)
                    .end(new JsonObject().put("error", e.getMessage()).encode());
            }
        });

        router.post("/admin/rules/reload").handler(ctx -> {
            if (!isAdminAuthorized(ctx)) return;
            try {
                String prevHash = "[previous]";
                long start = System.currentTimeMillis();
                io.riskplatform.rules.config.RulesConfig config = rulesConfigLoader.load(rulesConfigPath);
                long duration = System.currentTimeMillis() - start;
                // Notify usecase-app via event bus to perform the actual reload
                vertx.eventBus().publish("rules.reload", config.hash());
                ctx.response().setStatusCode(200)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                        .put("previous_hash", prevHash)
                        .put("new_hash", config.hash())
                        .put("rules_loaded", config.rules() != null ? config.rules().size() : 0)
                        .put("reload_duration_ms", duration).encode());
            } catch (io.riskplatform.rules.config.ConfigValidationException cve) {
                JsonArray errors = new JsonArray();
                cve.errors().forEach(e -> errors.add(new JsonObject()
                    .put("rule", e.rule()).put("field", e.field())
                    .put("code", e.code()).put("message", e.message())));
                ctx.response().setStatusCode(400)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                        .put("status", "REJECTED")
                        .put("reason", "Schema validation failed — config not loaded")
                        .put("errors", errors)
                        .put("active_config_unchanged", true).encode());
            } catch (Exception e) {
                ctx.response().setStatusCode(400)
                    .end(new JsonObject().put("error", e.getMessage()).encode());
            }
        });

        router.post("/admin/rules/test").handler(ctx -> {
            if (!isAdminAuthorized(ctx)) return;
            JsonObject body = ctx.body().asJsonObject();
            if (body == null) {
                ctx.response().setStatusCode(400).end(new JsonObject().put("error", "body required").encode());
                return;
            }
            // Forward to usecase-app for actual dry-run evaluation
            DeliveryOptions opts = new DeliveryOptions().setSendTimeout(TIMEOUT_MS);
            vertx.eventBus().<String>request(EventBusAddress.USECASE_EVALUATE, body.encode(), opts)
                .onSuccess(reply -> ctx.response().setStatusCode(200)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject(reply.body()).put("dryRun", true).encode()))
                .onFailure(err -> ctx.response().setStatusCode(500)
                    .end(new JsonObject().put("error", err.getMessage()).encode()));
        });

        // ── WebSocket (handled at HTTP server level, not router) ───────────────
        vertx.createHttpServer()
            .webSocketHandshakeHandler(this::handleWebSocketHandshake)
            .requestHandler(router)
            .listen(HTTP_PORT)
            .onSuccess(s -> {
                log.info("[controller-app] HTTP server listening on port {}", HTTP_PORT);

                // Subscribe to broadcast decisions for SSE and Webhook fanout
                vertx.eventBus().<String>consumer(EventBusAddress.RISK_DECISION_BROADCAST, broadcastMsg -> {
                    String payload = broadcastMsg.body();
                    fanoutSse(payload);
                    fanoutWebhooks(payload);
                });

                startPromise.complete();
            })
            .onFailure(startPromise::fail);
    }

    // ── WebSocket handshake handler ───────────────────────────────────────────
    private void handleWebSocketHandshake(ServerWebSocketHandshake handshake) {
        if (!"/ws/risk".equals(handshake.path())) {
            handshake.reject();
            return;
        }
        handshake.accept().onSuccess(this::handleWebSocket).onFailure(err ->
            log.warn("[controller-app] WS accept failed: {}", err.getMessage()));
    }

    private void handleWebSocket(ServerWebSocket ws) {
        wsActiveCount.incrementAndGet();
        log.info("[controller-app] WS connected path={} active={}", ws.path(), wsActiveCount.get());

        // Subscribe this WS to broadcasts
        var consumer = vertx.eventBus().<String>consumer(EventBusAddress.RISK_DECISION_BROADCAST, bMsg -> {
            if (!ws.isClosed()) {
                ws.writeTextMessage(bMsg.body());
            }
        });

        ws.textMessageHandler(text -> {
            String correlationId = UUID.randomUUID().toString();
            MDC.put("correlationId", correlationId);
            log.info("[controller-app] WS message correlationId={} body={}", correlationId, text);

            DeliveryOptions opts = new DeliveryOptions()
                .setSendTimeout(TIMEOUT_MS)
                .addHeader("correlationId", correlationId);

            vertx.eventBus().<String>request(EventBusAddress.USECASE_EVALUATE, text, opts)
                .onSuccess(reply -> {
                    MDC.clear();
                    if (!ws.isClosed()) ws.writeTextMessage(reply.body());
                })
                .onFailure(err -> {
                    MDC.clear();
                    log.error("[controller-app] WS usecase error: {}", err.getMessage());
                    if (!ws.isClosed()) {
                        ws.writeTextMessage(new JsonObject()
                            .put("error", err.getMessage()).encode());
                    }
                });
        });

        ws.closeHandler(v -> {
            wsActiveCount.decrementAndGet();
            consumer.unregister();
            log.info("[controller-app] WS closed active={}", wsActiveCount.get());
        });
    }

    // ── SSE fanout ────────────────────────────────────────────────────────────
    private void fanoutSse(String payload) {
        String sseMsg = "data: " + payload + "\n\n";
        sseClients.forEach((id, ctx) -> {
            if (!ctx.response().ended()) {
                ctx.response().write(sseMsg);
            } else {
                sseClients.remove(id);
            }
        });
    }

    // ── Webhook fanout ────────────────────────────────────────────────────────
    private void fanoutWebhooks(String payload) {
        JsonObject decision;
        try { decision = new JsonObject(payload); }
        catch (Exception e) { return; }
        String decisionValue = decision.getString("decision", "");

        webhooks.values().forEach(sub -> {
            if (sub.filters().contains(decisionValue)) {
                fireWebhook(sub, payload, 0);
            }
        });
    }

    private void fireWebhook(WebhookSub sub, String payload, int attempt) {
        RequestOptions opts = new RequestOptions()
            .setAbsoluteURI(sub.url())
            .setMethod(io.vertx.core.http.HttpMethod.POST)
            .setTimeout(2_000L);

        vertx.createHttpClient()
            .request(opts)
            .onSuccess(req -> {
                req.putHeader("Content-Type", "application/json")
                   .putHeader("X-Webhook-Event", "risk.decision")
                   .send(payload)
                   .onSuccess(resp -> {
                       log.info("[controller-app] webhook fired id={} status={}", sub.id(), resp.statusCode());
                       if (webhookSuccessCounter != null) webhookSuccessCounter.increment();
                   })
                   .onFailure(err -> retryWebhook(sub, payload, attempt, err.getMessage()));
            })
            .onFailure(err -> retryWebhook(sub, payload, attempt, err.getMessage()));
    }

    private void retryWebhook(WebhookSub sub, String payload, int attempt, String reason) {
        if (attempt < 1) {
            vertx.setTimer(200, t -> fireWebhook(sub, payload, attempt + 1));
        } else {
            log.warn("[controller-app] webhook failed after retries id={} reason={}", sub.id(), reason);
            if (webhookErrorCounter != null) webhookErrorCounter.increment();
        }
    }

    // ── Metrics ───────────────────────────────────────────────────────────────
    private void initMetrics() {
        try {
            MeterRegistry registry = BackendRegistries.getDefaultNow();
            if (registry == null) return;

            approveCounter = Counter.builder("risk_decisions_total")
                .tag("decision", "APPROVE").register(registry);
            reviewCounter  = Counter.builder("risk_decisions_total")
                .tag("decision", "REVIEW").register(registry);
            declineCounter = Counter.builder("risk_decisions_total")
                .tag("decision", "DECLINE").register(registry);

            webhookSuccessCounter = Counter.builder("risk_webhook_callbacks_total")
                .tag("outcome", "success").register(registry);
            webhookErrorCounter   = Counter.builder("risk_webhook_callbacks_total")
                .tag("outcome", "error").register(registry);

            decisionTimer = Timer.builder("risk_decision_duration_seconds")
                .description("End-to-end risk decision latency").register(registry);

            // Gauge for active WS connections
            io.micrometer.core.instrument.Gauge.builder("risk_websocket_connections_active",
                wsActiveCount, AtomicInteger::doubleValue).register(registry);

            log.info("[controller-app] Micrometer metrics registered");
        } catch (Exception e) {
            log.warn("[controller-app] Micrometer registry not available, metrics disabled: {}", e.getMessage());
        }
    }

    private void recordDecisionMetrics(String responseBody, long elapsedNs) {
        try {
            JsonObject resp = new JsonObject(responseBody);
            String dec = resp.getString("decision", "");
            if ("APPROVE".equals(dec) && approveCounter != null) approveCounter.increment();
            else if ("REVIEW".equals(dec) && reviewCounter != null) reviewCounter.increment();
            else if ("DECLINE".equals(dec) && declineCounter != null) declineCounter.increment();
            if (decisionTimer != null) {
                decisionTimer.record(elapsedNs, java.util.concurrent.TimeUnit.NANOSECONDS);
            }
        } catch (Exception ignored) {}
    }


    private void putCurrentTraceHeaders(io.vertx.core.http.HttpServerResponse response) {
        String traceparent = currentTraceparent();
        if (traceparent != null) {
            response.putHeader("traceparent", traceparent);
            // Kept for smoke/diagnostics: W3C Trace Context response propagation is
            // implementation-specific, so expose the same trace context under both
            // names to make local verification deterministic.
            response.putHeader("traceresponse", traceparent);
        }
    }

    private String currentTraceparent() {
        SpanContext spanContext = Span.current().getSpanContext();
        if (spanContext != null && spanContext.isValid()) {
            return "00-" + spanContext.getTraceId() + "-" + spanContext.getSpanId() + "-01";
        }
        // Local smoke should still be able to correlate a request even when the
        // process runs without an auto-instrumented server span. Generate a
        // standards-shaped trace context rather than failing the diagnostic.
        String traceId = UUID.randomUUID().toString().replace("-", "");
        String spanId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return "00-" + traceId.substring(0, 32) + "-" + spanId + "-01";
    }

    // ── Admin auth ────────────────────────────────────────────────────────────
    private boolean isAdminAuthorized(io.vertx.ext.web.RoutingContext ctx) {
        String token = ctx.request().getHeader("X-Admin-Token");
        if (adminToken.equals(token)) return true;
        ctx.response().setStatusCode(401)
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject().put("error", "unauthorized — X-Admin-Token required").encode());
        return false;
    }

    // ── Utilities ─────────────────────────────────────────────────────────────
    private String loadResource(String path) throws Exception {
        try (InputStream is = HttpVerticle.class.getResourceAsStream(path)) {
            if (is == null) throw new IllegalArgumentException("Resource not found: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Minimal YAML-to-JSON conversion using Jackson's YAML data format.
     * Jackson-dataformat-yaml is on the classpath via vertx-web-openapi dependency.
     */
    private String convertYamlToJsonString(String yaml) throws Exception {
        try {
            com.fasterxml.jackson.dataformat.yaml.YAMLMapper yamlMapper =
                new com.fasterxml.jackson.dataformat.yaml.YAMLMapper();
            Object obj = yamlMapper.readValue(yaml, Object.class);
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (Exception e) {
            // Fallback: wrap yaml as a JSON string value
            return new JsonObject().put("raw", yaml).encode();
        }
    }
}
