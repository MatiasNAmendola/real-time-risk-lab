package com.naranjax.monolith.repository;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Initializes the Postgres schema via plain JDBC.
 *
 * <p>Uses JDBC (not Vert.x reactive PG client) to keep the monolith's repository
 * layer simple and testable with standard Testcontainers patterns.
 * The schema is idempotent: safe to run on every startup.
 */
public final class DbBootstrap {

    private DbBootstrap() {}

    /**
     * Creates the customer_features table if absent, runs migrations, and seeds test data.
     * Runs on the calling thread — wrap in executeBlocking when called from a verticle.
     */
    public static Future<Void> init(String jdbcUrl, String user, String password) {
        return Vertx.currentContext() != null
            ? Vertx.currentContext().executeBlocking(() -> { doInit(jdbcUrl, user, password); return null; })
            : Future.succeededFuture((Void) null).onSuccess(v -> {
                try { doInit(jdbcUrl, user, password); } catch (SQLException e) { throw new RuntimeException(e); }
            });
    }

    public static void doInit(String jdbcUrl, String user, String password) throws SQLException {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, user, password);
             Statement stmt  = conn.createStatement()) {

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS customer_features (
                    customer_id          VARCHAR(64)       PRIMARY KEY,
                    risk_score           DOUBLE PRECISION  NOT NULL,
                    country              VARCHAR(8)        NOT NULL,
                    customer_age_days    INTEGER           NOT NULL DEFAULT 365,
                    chargeback_count_90d INTEGER           NOT NULL DEFAULT 0,
                    known_device         BOOLEAN           NOT NULL DEFAULT TRUE
                )
                """);

            stmt.execute("""
                ALTER TABLE customer_features
                  ADD COLUMN IF NOT EXISTS customer_age_days    INTEGER NOT NULL DEFAULT 365,
                  ADD COLUMN IF NOT EXISTS chargeback_count_90d INTEGER NOT NULL DEFAULT 0,
                  ADD COLUMN IF NOT EXISTS known_device         BOOLEAN NOT NULL DEFAULT TRUE
                """);

            stmt.execute("""
                INSERT INTO customer_features
                  (customer_id, risk_score, country, customer_age_days, chargeback_count_90d, known_device)
                VALUES
                  ('c-1', 0.82, 'AR', 365, 0, TRUE),
                  ('c-2', 0.30, 'MX', 365, 0, TRUE),
                  ('c-3', 0.55, 'BR',  20, 1, FALSE),
                  ('c-4', 0.95, 'AR', 365, 3, FALSE),
                  ('c-5', 0.10, 'CO',  10, 0, FALSE)
                ON CONFLICT (customer_id) DO NOTHING
                """);

            System.out.println("[monolith] DB initialized and seeded");
        }
    }
}
