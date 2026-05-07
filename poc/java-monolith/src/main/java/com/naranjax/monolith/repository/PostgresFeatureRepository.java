package com.naranjax.monolith.repository;

import com.naranjax.distributed.shared.FeatureSnapshot;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * JDBC-based feature repository.
 *
 * <p>Uses plain JDBC (org.postgresql:postgresql driver) for synchronous
 * blocking calls. Callers must wrap invocations in Vert.x executeBlocking
 * to avoid blocking the event loop.
 *
 * <p>Default snapshot returned for unknown customers: riskScore=0.5 (conservative).
 */
public class PostgresFeatureRepository {

    private static final String QUERY =
        "SELECT customer_id, risk_score, country, customer_age_days, " +
        "chargeback_count_90d, known_device " +
        "FROM customer_features WHERE customer_id = ?";

    private final String jdbcUrl;
    private final String user;
    private final String password;

    public PostgresFeatureRepository() {
        String host = System.getenv().getOrDefault("PG_HOST",     "postgres");
        int    port = Integer.parseInt(System.getenv().getOrDefault("PG_PORT", "5432"));
        String db   = System.getenv().getOrDefault("PG_DATABASE", "naranjax");
        this.jdbcUrl  = "jdbc:postgresql://" + host + ":" + port + "/" + db;
        this.user     = System.getenv().getOrDefault("PG_USER",     "naranjax");
        this.password = SecretsBootstrap.resolveDbPassword();
    }

    /** Constructor for testing — accepts explicit JDBC URL. */
    public PostgresFeatureRepository(String jdbcUrl, String user, String password) {
        this.jdbcUrl  = jdbcUrl;
        this.user     = user;
        this.password = password;
    }

    /**
     * Loads a customer feature snapshot from Postgres.
     * Returns a default snapshot if the customer is not found.
     * This method BLOCKS — always call from a worker thread or executeBlocking.
     */
    public FeatureSnapshot findByCustomerId(String customerId) throws SQLException {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, user, password);
             PreparedStatement ps = conn.prepareStatement(QUERY)) {
            ps.setString(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return new FeatureSnapshot(customerId, 0.5, "UNKNOWN", 365, 0, true);
                }
                return new FeatureSnapshot(
                    rs.getString("customer_id"),
                    rs.getDouble("risk_score"),
                    rs.getString("country"),
                    rs.getInt("customer_age_days"),
                    rs.getInt("chargeback_count_90d"),
                    rs.getBoolean("known_device")
                );
            }
        }
    }
}
