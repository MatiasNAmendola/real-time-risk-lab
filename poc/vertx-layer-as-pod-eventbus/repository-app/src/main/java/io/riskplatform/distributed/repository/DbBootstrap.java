package io.riskplatform.distributed.repository;

import io.riskplatform.distributed.repository.secrets.SecretsBootstrap;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;

public class DbBootstrap {

    public static Future<Pool> init(Vertx vertx) {
        String host     = System.getenv().getOrDefault("PG_HOST",     "postgres");
        int    port     = Integer.parseInt(System.getenv().getOrDefault("PG_PORT", "5432"));
        String database = System.getenv().getOrDefault("PG_DATABASE", "riskplatform");
        String user     = System.getenv().getOrDefault("PG_USER",     "riskplatform");
        // Resolve password from Moto Secrets Manager or OpenBao if configured, else env var
        String password = SecretsBootstrap.resolveDbPassword();

        PgConnectOptions connectOptions = new PgConnectOptions()
            .setHost(host)
            .setPort(port)
            .setDatabase(database)
            .setUser(user)
            .setPassword(password);

        PoolOptions poolOptions = new PoolOptions().setMaxSize(5);

        Pool pool = PgBuilder.pool()
            .with(poolOptions)
            .connectingTo(connectOptions)
            .using(vertx)
            .build();

        String ddl = """
            CREATE TABLE IF NOT EXISTS customer_features (
                customer_id          VARCHAR(64)       PRIMARY KEY,
                risk_score           DOUBLE PRECISION  NOT NULL,
                country              VARCHAR(8)        NOT NULL,
                customer_age_days    INTEGER           NOT NULL DEFAULT 365,
                chargeback_count_90d INTEGER           NOT NULL DEFAULT 0,
                known_device         BOOLEAN           NOT NULL DEFAULT TRUE
            );
            """;

        // ALTER existing table to add new columns if they don't exist yet (idempotent migration).
        String migrate = """
            ALTER TABLE customer_features
              ADD COLUMN IF NOT EXISTS customer_age_days    INTEGER NOT NULL DEFAULT 365,
              ADD COLUMN IF NOT EXISTS chargeback_count_90d INTEGER NOT NULL DEFAULT 0,
              ADD COLUMN IF NOT EXISTS known_device         BOOLEAN NOT NULL DEFAULT TRUE;
            """;

        String seed = """
            INSERT INTO customer_features
              (customer_id, risk_score, country, customer_age_days, chargeback_count_90d, known_device)
            VALUES
              ('c-1', 0.82, 'AR', 365,  0, TRUE),
              ('c-2', 0.30, 'MX', 365,  0, TRUE),
              ('c-3', 0.55, 'BR',  20,  1, FALSE),
              ('c-4', 0.95, 'AR', 365,  3, FALSE),
              ('c-5', 0.10, 'CO',  10,  0, FALSE)
            ON CONFLICT (customer_id) DO NOTHING;
            """;

        return pool.query(ddl).execute()
            .flatMap(r -> pool.query(migrate).execute())
            .flatMap(r -> pool.query(seed).execute())
            .map(r -> {
                System.out.println("[repository-app] DB initialized and seeded");
                return pool;
            });
    }
}
