package io.riskplatform.engine.infrastructure.controller;

import io.riskplatform.engine.domain.context.ExecutionContext;
import io.riskplatform.engine.application.common.StructuredLogger;
import io.riskplatform.engine.application.dto.EvaluateRiskRequestDTO;
import io.riskplatform.engine.application.mapper.RiskDecisionMapper;
import io.riskplatform.engine.domain.entity.CorrelationId;
import io.riskplatform.engine.domain.usecase.EvaluateRiskUseCase;
import io.riskplatform.rules.audit.RulesAuditTrail;
import io.riskplatform.rules.config.RulesConfig;
import io.riskplatform.rules.config.RulesConfigLoader;
import io.riskplatform.rules.engine.FeatureSnapshot;
import io.riskplatform.rules.engine.AggregateDecision;
import io.riskplatform.rules.engine.RuleEngine;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * Inbound adapter — HTTP controller backed by com.sun.net.httpserver.HttpServer (stdlib).
 * No framework, no external deps — this is the point of the PoC.
 *
 * Admin endpoints (all require X-Admin-Token header):
 *   GET  /admin/rules         — list active rules + version hash
 *   POST /admin/rules/reload  — force reload from RULES_CONFIG_PATH
 *   POST /admin/rules/test    — dry-run evaluation (no audit entry)
 *   GET  /admin/rules/audit   — last 100 audit entries
 */
public final class HttpController {
    private final EvaluateRiskUseCase useCase;
    private final StructuredLogger logger;
    private final HttpServer server;
    private final RuleEngine ruleEngine;
    private final RulesAuditTrail rulesAuditTrail;
    private final RulesConfigLoader rulesConfigLoader;
    private final String adminToken;
    private final String rulesConfigPath;

    public HttpController(EvaluateRiskUseCase useCase, StructuredLogger logger, int port) throws IOException {
        this(useCase, logger, port, null, null);
    }

    public HttpController(EvaluateRiskUseCase useCase, StructuredLogger logger, int port,
                          RuleEngine ruleEngine, RulesAuditTrail rulesAuditTrail) throws IOException {
        this.useCase          = useCase;
        this.logger           = logger.with("adapter", "http");
        this.ruleEngine       = ruleEngine;
        this.rulesAuditTrail  = rulesAuditTrail;
        this.rulesConfigLoader = new RulesConfigLoader();
        this.adminToken       = System.getenv().getOrDefault("ADMIN_TOKEN", "change-me-admin-token");
        this.rulesConfigPath  = System.getenv().getOrDefault(
                "RULES_CONFIG_PATH", "examples/rules-config/v1/rules.yaml");
        this.server  = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        server.createContext("/healthz",          this::handleHealthz);
        server.createContext("/readyz",           this::handleReadyz);
        server.createContext("/risk",             this::handleRisk);
        server.createContext("/admin/rules",      this::handleAdminRules);
        server.createContext("/",                 this::handleRoot);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("shutdown signal received — stopping HTTP server", "grace_seconds", 5);
            server.stop(5);
        }));
    }

    public void start() {
        server.start();
        logger.info("HTTP server started", "port", server.getAddress().getPort());
    }

    /** Returns the bound port — useful when the server was created with port 0 (random). */
    public int port() {
        return server.getAddress().getPort();
    }

    public void stop(int delaySeconds) {
        server.stop(delaySeconds);
    }

    // ------------------------------------------------------------------ handlers

    private void handleHealthz(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, Map.of("error", "method not allowed"));
            return;
        }
        sendJson(ex, 200, Map.of("status", "ok"));
    }

    private void handleReadyz(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, Map.of("error", "method not allowed"));
            return;
        }
        sendJson(ex, 200, Map.of("status", "ready"));
    }

    private void handleRoot(HttpExchange ex) throws IOException {
        if (!"/".equals(ex.getRequestURI().getPath())) {
            sendJson(ex, 404, Map.of("error", "not found"));
            return;
        }
        byte[] body = "Risk engine up. POST /risk to evaluate.".getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
        ex.sendResponseHeaders(200, body.length);
        ex.getResponseBody().write(body);
        ex.getResponseBody().close();
    }

    private void handleRisk(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, Map.of("error", "method not allowed"));
            return;
        }

        // derive correlation ID from header or generate one
        String correlationId = ex.getRequestHeaders().getFirst("X-Correlation-Id");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        String traceId = UUID.randomUUID().toString();

        long startNs = System.nanoTime();
        Map<String, Object> reqFields = null;

        try {
            String rawBody = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).strip();
            if (rawBody.isEmpty()) {
                sendJson(ex, 400, Map.of("error", "request body is empty"));
                return;
            }
            try {
                reqFields = MiniJson.parse(rawBody);
            } catch (IllegalArgumentException e) {
                sendJson(ex, 400, Map.of("error", "invalid JSON: " + e.getMessage()));
                return;
            }

            String transactionId = stringField(reqFields, "transactionId");
            String customerId    = stringField(reqFields, "customerId");
            Long   amountCents   = longField(reqFields, "amountCents");

            if (transactionId == null || customerId == null || amountCents == null) {
                sendJson(ex, 400, Map.of("error", "required fields: transactionId, customerId, amountCents"));
                return;
            }

            // optional fields
            String bodyCorrelation = stringField(reqFields, "correlationId");
            if (bodyCorrelation != null && !bodyCorrelation.isBlank()) correlationId = bodyCorrelation;
            String idempotencyKey = stringField(reqFields, "idempotencyKey");
            if (idempotencyKey == null) idempotencyKey = UUID.randomUUID().toString();

            var dto = new EvaluateRiskRequestDTO(
                    transactionId, customerId, amountCents, "ARS",
                    false, correlationId, idempotencyKey
            );
            var request = RiskDecisionMapper.toDomain(dto);
            var context = new ExecutionContext(
                    new CorrelationId(correlationId),
                    logger.with("correlationId", correlationId).with("traceId", traceId)
            );

            var decision = useCase.evaluate(context, request, Duration.ofMillis(500));
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000;

            logger.info("risk evaluated",
                    "correlationId", correlationId,
                    "traceId", traceId,
                    "decision", decision.decision().name(),
                    "latencyMs", latencyMs
            );

            var responseFields = new LinkedHashMap<String, Object>();
            responseFields.put("decision",      decision.decision().name());
            responseFields.put("reason",        decision.reason());
            responseFields.put("correlationId", correlationId);
            responseFields.put("traceId",       traceId);
            sendJson(ex, 200, responseFields);

        } catch (Exception e) {
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000;
            logger.error(e, "use case failed",
                    "correlationId", correlationId,
                    "traceId", traceId,
                    "latencyMs", latencyMs
            );
            sendJson(ex, 500, Map.of(
                    "error",         "internal error",
                    "correlationId", correlationId,
                    "traceId",       traceId
            ));
        }
    }

    // ------------------------------------------------------------------ admin API

    private void handleAdminRules(HttpExchange ex) throws IOException {
        if (!checkAdminToken(ex)) return;

        String path   = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod().toUpperCase();

        if (ruleEngine == null) {
            sendJson(ex, 503, Map.of("error", "rules engine not initialised"));
            return;
        }

        // POST /admin/rules/reload
        if ("POST".equals(method) && path.endsWith("/reload")) {
            try {
                String prevHash = ruleEngine.activeConfigHash();
                long startMs = System.currentTimeMillis();
                RulesConfig newConfig = rulesConfigLoader.load(rulesConfigPath);
                ruleEngine.reload(newConfig);
                long durationMs = System.currentTimeMillis() - startMs;
                sendJson(ex, 200, Map.of(
                        "previous_hash", prevHash,
                        "new_hash", newConfig.hash(),
                        "rules_loaded", newConfig.rules() != null ? newConfig.rules().size() : 0,
                        "reload_duration_ms", durationMs));
            } catch (io.riskplatform.rules.config.ConfigValidationException cve) {
                List<Map<String, String>> errors = cve.errors().stream()
                        .map(e2 -> Map.of("rule", e2.rule(), "field", e2.field(),
                                "code", e2.code(), "message", e2.message()))
                        .toList();
                sendJson(ex, 400, Map.of(
                        "status", "REJECTED",
                        "reason", "Schema validation failed — config not loaded",
                        "errors", errors,
                        "active_config_unchanged", true,
                        "active_config_hash", ruleEngine.activeConfigHash(),
                        "active_config_version", ruleEngine.activeConfig().version()));
            } catch (Exception e) {
                sendJson(ex, 400, Map.of("error", e.getMessage()));
            }
            return;
        }

        // POST /admin/rules/test — dry-run evaluation
        if ("POST".equals(method) && path.endsWith("/test")) {
            try {
                String rawBody = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).strip();
                Map<String, Object> body = MiniJson.parse(rawBody);
                FeatureSnapshot snap = FeatureSnapshot.builder()
                        .customerId(stringField(body, "customerId"))
                        .transactionId(stringField(body, "transactionId"))
                        .amountCents(longField(body, "amountCents") != null ? longField(body, "amountCents") : 0L)
                        .newDevice(Boolean.TRUE.equals(body.get("newDevice")))
                        .customerAgeDays(body.get("customerAgeDays") instanceof Number n ? n.intValue() : 0)
                        .merchantMcc(stringField(body, "merchantMcc"))
                        .country(stringField(body, "country"))
                        .build();
                // Evaluate without writing to audit trail — dry-run
                AggregateDecision decision = ruleEngine.evaluate(snap);
                sendJson(ex, 200, Map.of(
                        "decision", decision.decision().name(),
                        "triggeredRules", decision.triggeredRuleNames(),
                        "rulesVersionHash", decision.rulesVersionHash(),
                        "evalMs", decision.evalMs(),
                        "dryRun", true));
            } catch (Exception e) {
                sendJson(ex, 400, Map.of("error", e.getMessage()));
            }
            return;
        }

        // GET /admin/rules/audit
        if ("GET".equals(method) && path.endsWith("/audit")) {
            if (rulesAuditTrail == null) {
                sendJson(ex, 503, Map.of("error", "audit trail not available"));
                return;
            }
            List<Map<String, Object>> entries = rulesAuditTrail.recent(100).stream()
                    .map(entry -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("timestamp", entry.timestamp().toString());
                        m.put("transactionId", entry.transactionId());
                        m.put("customerId", entry.customerId());
                        m.put("decision", entry.decision().name());
                        m.put("triggeredRules", entry.triggeredRules());
                        m.put("rulesVersionHash", entry.rulesVersionHash());
                        m.put("rulesVersion", entry.rulesVersion());
                        m.put("fallbackApplied", entry.fallbackApplied());
                        m.put("evalMs", entry.evalMs());
                        return m;
                    }).toList();
            sendJson(ex, 200, Map.of("entries", entries, "total", entries.size()));
            return;
        }

        // GET /admin/rules — list active rules
        if ("GET".equals(method)) {
            RulesConfig config = ruleEngine.activeConfig();
            List<Map<String, Object>> rules = config.rules() != null
                    ? config.rules().stream().map(r -> {
                        Map<String, Object> rm = new LinkedHashMap<>();
                        rm.put("name", r.name());
                        rm.put("version", r.version());
                        rm.put("type", r.type());
                        rm.put("enabled", r.enabled());
                        rm.put("action", r.action());
                        rm.put("weight", r.weight());
                        return rm;
                      }).toList()
                    : List.of();
            sendJson(ex, 200, Map.of(
                    "version", config.version(),
                    "hash", config.hash(),
                    "deployed_at", config.deployedAt() != null ? config.deployedAt() : "",
                    "deployed_by", config.deployedBy() != null ? config.deployedBy() : "",
                    "rules", rules,
                    "total", rules.size(),
                    "enabled_count", config.enabledCount()));
            return;
        }

        sendJson(ex, 405, Map.of("error", "method not allowed"));
    }

    private boolean checkAdminToken(HttpExchange ex) throws IOException {
        String token = ex.getRequestHeaders().getFirst("X-Admin-Token");
        if (adminToken.equals(token)) return true;
        sendJson(ex, 401, Map.of("error", "unauthorized — X-Admin-Token required"));
        return false;
    }

    // ------------------------------------------------------------------ helpers

    private static void sendJson(HttpExchange ex, int status, Map<?, ?> body) throws IOException {
        byte[] bytes = MiniJson.stringify(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.getResponseBody().close();
    }

    private static String stringField(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v instanceof String s ? s : null;
    }

    private static Long longField(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof Long l) return l;
        if (v instanceof Number n) return n.longValue();
        return null;
    }
}
