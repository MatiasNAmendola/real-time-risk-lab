package io.riskplatform.monolith.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.riskplatform.monolith.usecase.MonolithEventBusAddress;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
import io.vertx.micrometer.backends.BackendRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HTTP server verticle for the java-monolith PoC.
 *
 * <p>Exposes the same API surface as the java-vertx-distributed controller-app
 * but communicates with the use-case layer via the LOCAL event bus (no Hazelcast).
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /risk                    — synchronous REST evaluation
 *   <li>GET  /risk/stream             — SSE push of decisions
 *   <li>WS   /ws/risk                 — bidirectional WebSocket
 *   <li>POST /webhooks                — subscribe to decision events
 *   <li>GET  /webhooks                — list subscriptions
 *   <li>DELETE /webhooks/{id}         — unsubscribe
 *   <li>GET  /admin/rules             — list active rules (X-Admin-Token)
 *   <li>POST /admin/rules/reload      — hot reload rules config
 *   <li>POST /admin/rules/test        — dry-run evaluation
 *   <li>GET  /admin/rules/audit       — last audit entries
 *   <li>GET  /healthz                 — liveness probe
 *   <li>GET  /readyz                  — readiness probe
 *   <li>GET  /openapi.json            — OpenAPI spec as JSON
 *   <li>GET  /openapi.yaml            — OpenAPI spec as YAML
 *   <li>GET  /asyncapi.json           — AsyncAPI spec as JSON
 *   <li>GET  /docs                    — Swagger UI
 * </ul>
 */
public class HttpServerVerticle extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(HttpServerVerticle.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int HTTP_PORT = 8090;
    private static final long TIMEOUT_MS = 15_000L;

    private final ConcurrentHashMap<String, io.vertx.ext.web.RoutingContext> sseClients = new ConcurrentHashMap<>();
    record WebhookSub(String id, String url, List<String> filters) {}
    private final ConcurrentHashMap<String, WebhookSub> webhooks = new ConcurrentHashMap<>();
    private final AtomicInteger wsActiveCount = new AtomicInteger(0);

    private final String adminToken = System.getenv().getOrDefault("ADMIN_TOKEN", "admin-secret");
    private final String rulesConfigPath = System.getenv().getOrDefault(
            "RULES_CONFIG_PATH", "examples/rules-config/v1/rules.yaml");

    private Counter approveCounter;
    private Counter reviewCounter;
    private Counter declineCounter;
    private Counter webhookSuccessCounter;
    private Counter webhookErrorCounter;
    private Timer   decisionTimer;

    @Override
    public void start(Promise<Void> startPromise) {
        initMetrics();

        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        // POST /risk
        router.post("/risk").handler(ctx -> {
            String correlationId = ctx.request().getHeader("X-Correlation-Id");
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = UUID.randomUUID().toString();
            }
            final String corrId = correlationId;
            ctx.response().putHeader("X-Correlation-Id", corrId);
            MDC.put("correlationId", corrId);

            String idempotencyKey = ctx.request().getHeader("Idempotency-Key");

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

            body.put("correlationId", corrId);
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                body.put("idempotencyKey", idempotencyKey);
            }

            DeliveryOptions opts = new DeliveryOptions()
                .setSendTimeout(TIMEOUT_MS)
                .addHeader("correlationId", corrId);

            long startNs = System.nanoTime();
            vertx.eventBus().<String>request(MonolithEventBusAddress.USECASE_EVALUATE, body.encode(), opts)
                .onSuccess(reply -> {
                    long elapsed = System.nanoTime() - startNs;
                    recordDecisionMetrics(reply.body(), elapsed);
                    MDC.clear();
                    ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .end(reply.body());
                })
                .onFailure(err -> {
                    log.error("[monolith] usecase error correlationId={}: {}", corrId, err.getMessage());
                    MDC.clear();
                    ctx.response().setStatusCode(502)
                        .putHeader("Content-Type", "application/json")
                        .end(new JsonObject().put("error", err.getMessage()).encode());
                });
        });

        // GET /risk/stream (SSE)
        router.get("/risk/stream").handler(ctx -> {
            String clientId = UUID.randomUUID().toString();
            ctx.response()
                .putHeader("Content-Type", "text/event-stream")
                .putHeader("Cache-Control", "no-cache")
                .putHeader("X-Accel-Buffering", "no")
                .setChunked(true);

            long timerId = vertx.setPeriodic(15_000, t -> {
                if (!ctx.response().ended()) {
                    ctx.response().write(": keep-alive\n\n");
                }
            });

            sseClients.put(clientId, ctx);
            ctx.request().connection().closeHandler(v -> {
                sseClients.remove(clientId);
                vertx.cancelTimer(timerId);
            });
        });

        // Webhook CRUD
        router.post("/webhooks").handler(ctx -> {
            JsonObject body;
            try { body = ctx.body().asJsonObject(); }
            catch (Exception e) {
                ctx.response().setStatusCode(400).end(new JsonObject().put("error","invalid JSON").encode());
                return;
            }
            String url    = body.getString("url");
            String filter = body.getString("filter", "APPROVE,REVIEW,DECLINE");
            if (url == null || url.isBlank()) {
                ctx.response().setStatusCode(400).end(new JsonObject().put("error","url required").encode());
                return;
            }
            String id = UUID.randomUUID().toString();
            webhooks.put(id, new WebhookSub(id, url, List.of(filter.split(","))));
            ctx.response().setStatusCode(201)
                .putHeader("Content-Type","application/json")
                .end(new JsonObject().put("id", id).put("filter", filter).encode());
        });

        router.get("/webhooks").handler(ctx -> {
            JsonArray arr = new JsonArray();
            webhooks.values().forEach(sub ->
                arr.add(new JsonObject()
                    .put("id", sub.id())
                    .put("filter", String.join(",", sub.filters()))));
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

        // Admin Rules API
        router.get("/admin/rules").handler(ctx -> {
            if (!isAdminAuthorized(ctx)) return;
            try {
                var loader = new io.riskplatform.rules.config.RulesConfigLoader();
                var config = loader.load(rulesConfigPath);
                JsonArray rulesArr = new JsonArray();
                if (config.rules() != null) {
                    config.rules().forEach(r -> rulesArr.add(new JsonObject()
                        .put("name", r.name()).put("type", r.type())
                        .put("enabled", r.enabled()).put("action", r.action())));
                }
                ctx.response().setStatusCode(200)
                    .putHeader("Content-Type","application/json")
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
                long start = System.currentTimeMillis();
                var loader = new io.riskplatform.rules.config.RulesConfigLoader();
                var config = loader.load(rulesConfigPath);
                long duration = System.currentTimeMillis() - start;
                vertx.eventBus().publish("rules.reload", config.hash());
                ctx.response().setStatusCode(200)
                    .putHeader("Content-Type","application/json")
                    .end(new JsonObject()
                        .put("new_hash", config.hash())
                        .put("rules_loaded", config.rules() != null ? config.rules().size() : 0)
                        .put("reload_duration_ms", duration).encode());
            } catch (io.riskplatform.rules.config.ConfigValidationException cve) {
                JsonArray errors = new JsonArray();
                cve.errors().forEach(e -> errors.add(new JsonObject()
                    .put("rule", e.rule()).put("field", e.field())
                    .put("code", e.code()).put("message", e.message())));
                ctx.response().setStatusCode(400)
                    .putHeader("Content-Type","application/json")
                    .end(new JsonObject()
                        .put("status","REJECTED")
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
                ctx.response().setStatusCode(400).end(new JsonObject().put("error","body required").encode());
                return;
            }
            DeliveryOptions opts = new DeliveryOptions().setSendTimeout(TIMEOUT_MS);
            vertx.eventBus().<String>request(MonolithEventBusAddress.USECASE_EVALUATE, body.encode(), opts)
                .onSuccess(reply -> ctx.response().setStatusCode(200)
                    .putHeader("Content-Type","application/json")
                    .end(new JsonObject(reply.body()).put("dryRun", true).encode()))
                .onFailure(err -> ctx.response().setStatusCode(500)
                    .end(new JsonObject().put("error", err.getMessage()).encode()));
        });

        router.get("/admin/rules/audit").handler(ctx -> {
            if (!isAdminAuthorized(ctx)) return;
            ctx.response().setStatusCode(200)
                .putHeader("Content-Type","application/json")
                .end(new JsonObject().put("message","audit trail available in logs").encode());
        });

        // OpenAPI / AsyncAPI / Swagger UI
        router.get("/openapi.json").handler(ctx -> {
            try {
                ctx.response().putHeader("Content-Type","application/json")
                    .end(convertYamlToJsonString(loadResource("/openapi.yaml")));
            } catch (Exception e) {
                ctx.response().setStatusCode(500).end("spec not found: " + e.getMessage());
            }
        });

        router.get("/openapi.yaml").handler(ctx -> {
            try {
                ctx.response().putHeader("Content-Type","application/yaml")
                    .end(loadResource("/openapi.yaml"));
            } catch (Exception e) { ctx.response().setStatusCode(500).end("spec not found"); }
        });

        router.get("/asyncapi.json").handler(ctx -> {
            try {
                ctx.response().putHeader("Content-Type","application/json")
                    .end(convertYamlToJsonString(loadResource("/asyncapi.yaml")));
            } catch (Exception e) {
                ctx.response().setStatusCode(500).end("spec not found: " + e.getMessage());
            }
        });

        router.get("/docs").handler(ctx -> ctx.redirect("/docs/"));
        router.get("/docs/").handler(ctx -> {
            String html = """
                <!DOCTYPE html><html><head>
                  <title>Risk API (Monolith) — Swagger UI</title>
                  <meta charset="utf-8"/>
                  <link rel="stylesheet" href="https://unpkg.com/swagger-ui-dist@5/swagger-ui.css">
                </head><body>
                <div id="swagger-ui"></div>
                <script src="https://unpkg.com/swagger-ui-dist@5/swagger-ui-bundle.js"></script>
                <script>SwaggerUIBundle({url:'/openapi.yaml',dom_id:'#swagger-ui',
                  presets:[SwaggerUIBundle.presets.apis,SwaggerUIBundle.SwaggerUIStandalonePreset],
                  layout:'BaseLayout'});</script>
                <p style="margin:20px">AsyncAPI: <a href="/asyncapi.json">/asyncapi.json</a></p>
                </body></html>
                """;
            ctx.response().putHeader("Content-Type","text/html").end(html);
        });

        // Health probes
        router.get("/healthz").handler(ctx ->
            ctx.response().setStatusCode(200)
                .putHeader("Content-Type","application/json")
                .end(new JsonObject().put("status","UP").put("service","java-monolith").encode()));

        router.get("/readyz").handler(ctx ->
            ctx.response().setStatusCode(200)
                .putHeader("Content-Type","application/json")
                .end(new JsonObject().put("ready", true).encode()));

        // HTTP server with WS support
        vertx.createHttpServer()
            .webSocketHandshakeHandler(this::handleWebSocketHandshake)
            .requestHandler(router)
            .listen(HTTP_PORT)
            .onSuccess(s -> {
                log.info("[monolith] HTTP server listening on port {}", HTTP_PORT);
                vertx.eventBus().<String>consumer(MonolithEventBusAddress.RISK_DECISION_BROADCAST, bMsg -> {
                    fanoutSse(bMsg.body());
                    fanoutWebhooks(bMsg.body());
                });
                startPromise.complete();
            })
            .onFailure(startPromise::fail);
    }

    private void handleWebSocketHandshake(ServerWebSocketHandshake handshake) {
        if (!"/ws/risk".equals(handshake.path())) {
            handshake.reject();
            return;
        }
        handshake.accept().onSuccess(this::handleWebSocket).onFailure(err ->
            log.warn("[monolith] WS accept failed: {}", err.getMessage()));
    }

    private void handleWebSocket(ServerWebSocket ws) {
        wsActiveCount.incrementAndGet();
        var consumer = vertx.eventBus().<String>consumer(MonolithEventBusAddress.RISK_DECISION_BROADCAST, bMsg -> {
            if (!ws.isClosed()) ws.writeTextMessage(bMsg.body());
        });

        ws.textMessageHandler(text -> {
            String correlationId = UUID.randomUUID().toString();
            DeliveryOptions opts = new DeliveryOptions()
                .setSendTimeout(TIMEOUT_MS)
                .addHeader("correlationId", correlationId);
            vertx.eventBus().<String>request(MonolithEventBusAddress.USECASE_EVALUATE, text, opts)
                .onSuccess(reply -> { if (!ws.isClosed()) ws.writeTextMessage(reply.body()); })
                .onFailure(err -> {
                    if (!ws.isClosed()) ws.writeTextMessage(new JsonObject().put("error", err.getMessage()).encode());
                });
        });

        ws.closeHandler(v -> {
            wsActiveCount.decrementAndGet();
            consumer.unregister();
        });
    }

    private void fanoutSse(String payload) {
        String msg = "data: " + payload + "\n\n";
        sseClients.forEach((id, ctx) -> {
            if (!ctx.response().ended()) ctx.response().write(msg);
            else sseClients.remove(id);
        });
    }

    private void fanoutWebhooks(String payload) {
        JsonObject decision;
        try { decision = new JsonObject(payload); } catch (Exception e) { return; }
        String decisionValue = decision.getString("decision", "");
        webhooks.values().forEach(sub -> {
            if (sub.filters().contains(decisionValue)) fireWebhook(sub, payload, 0);
        });
    }

    private void fireWebhook(WebhookSub sub, String payload, int attempt) {
        RequestOptions opts = new RequestOptions()
            .setAbsoluteURI(sub.url())
            .setMethod(io.vertx.core.http.HttpMethod.POST)
            .setTimeout(2_000L);
        vertx.createHttpClient()
            .request(opts)
            .onSuccess(req -> req.putHeader("Content-Type","application/json")
                .putHeader("X-Webhook-Event","risk.decision")
                .send(payload)
                .onSuccess(resp -> { if (webhookSuccessCounter != null) webhookSuccessCounter.increment(); })
                .onFailure(err -> retryWebhook(sub, payload, attempt, err.getMessage())))
            .onFailure(err -> retryWebhook(sub, payload, attempt, err.getMessage()));
    }

    private void retryWebhook(WebhookSub sub, String payload, int attempt, String reason) {
        if (attempt < 1) {
            vertx.setTimer(200, t -> fireWebhook(sub, payload, attempt + 1));
        } else {
            log.warn("[monolith] webhook failed id={} reason={}", sub.id(), reason);
            if (webhookErrorCounter != null) webhookErrorCounter.increment();
        }
    }

    private void initMetrics() {
        try {
            MeterRegistry registry = BackendRegistries.getDefaultNow();
            if (registry == null) return;
            approveCounter = Counter.builder("risk_decisions_total").tag("decision","APPROVE").register(registry);
            reviewCounter  = Counter.builder("risk_decisions_total").tag("decision","REVIEW").register(registry);
            declineCounter = Counter.builder("risk_decisions_total").tag("decision","DECLINE").register(registry);
            webhookSuccessCounter = Counter.builder("risk_webhook_callbacks_total").tag("outcome","success").register(registry);
            webhookErrorCounter   = Counter.builder("risk_webhook_callbacks_total").tag("outcome","error").register(registry);
            decisionTimer = Timer.builder("risk_decision_duration_seconds")
                .description("End-to-end risk decision latency").register(registry);
            io.micrometer.core.instrument.Gauge.builder("risk_websocket_connections_active",
                wsActiveCount, AtomicInteger::doubleValue).register(registry);
        } catch (Exception e) {
            log.warn("[monolith] Micrometer not available: {}", e.getMessage());
        }
    }

    private void recordDecisionMetrics(String responseBody, long elapsedNs) {
        try {
            JsonObject resp = new JsonObject(responseBody);
            String dec = resp.getString("decision","");
            if ("APPROVE".equals(dec) && approveCounter != null) approveCounter.increment();
            else if ("REVIEW".equals(dec) && reviewCounter != null) reviewCounter.increment();
            else if ("DECLINE".equals(dec) && declineCounter != null) declineCounter.increment();
            if (decisionTimer != null) decisionTimer.record(elapsedNs, java.util.concurrent.TimeUnit.NANOSECONDS);
        } catch (Exception ignored) {}
    }

    private boolean isAdminAuthorized(io.vertx.ext.web.RoutingContext ctx) {
        String token = ctx.request().getHeader("X-Admin-Token");
        if (adminToken.equals(token)) return true;
        ctx.response().setStatusCode(401)
            .putHeader("Content-Type","application/json")
            .end(new JsonObject().put("error","unauthorized — X-Admin-Token required").encode());
        return false;
    }

    private String loadResource(String path) throws Exception {
        try (InputStream is = HttpServerVerticle.class.getResourceAsStream(path)) {
            if (is == null) throw new IllegalArgumentException("Resource not found: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String convertYamlToJsonString(String yaml) throws Exception {
        try {
            var yamlMapper = new com.fasterxml.jackson.dataformat.yaml.YAMLMapper();
            Object obj = yamlMapper.readValue(yaml, Object.class);
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (Exception e) {
            return new JsonObject().put("raw", yaml).encode();
        }
    }
}
