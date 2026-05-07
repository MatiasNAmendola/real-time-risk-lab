package com.naranjax.interview.risk.cmd;

import com.naranjax.interview.risk.application.common.StructuredLogger;
import com.naranjax.interview.risk.config.RiskApplicationFactory;
import com.naranjax.interview.risk.infrastructure.controller.HttpController;
import com.naranjax.interview.risk.infrastructure.repository.log.ConsoleStructuredLogger;

/**
 * Inbound adapter — HTTP entry point.
 * Analogous to CliRunner: wires the app via RiskApplicationFactory,
 * creates an HttpController, and blocks until SIGTERM.
 *
 * Usage: java ... HttpRunner [--port PORT]
 * Default port: 8081 (distinct from the Vert.x distributed PoC on 8080).
 */
public final class HttpRunner {
    public static void main(String[] args) throws Exception {
        int port = 8081;
        for (int i = 0; i < args.length - 1; i++) {
            if ("--port".equals(args[i])) {
                port = Integer.parseInt(args[i + 1]);
            }
        }

        var logger = new ConsoleStructuredLogger().with("service", "risk-engine-http");
        logger.info("starting HTTP server", "port", port);

        try (var app = new RiskApplicationFactory()) {
            var controller = new HttpController(app.evaluateRiskUseCase(), logger, port,
                    app.ruleEngine(), app.rulesAuditTrail());
            controller.start();

            // Block main thread until shutdown hook fires (SIGTERM / SIGINT)
            Thread.currentThread().join();
        }
    }
}
