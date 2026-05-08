package io.riskplatform.monolith.config;

import io.riskplatform.monolith.controller.HttpServerVerticle;
import io.riskplatform.monolith.repository.DbBootstrap;
import io.riskplatform.monolith.repository.KafkaDecisionPublisher;
import io.riskplatform.monolith.repository.PostgresFeatureRepository;
import io.riskplatform.monolith.repository.S3AuditPublisher;
import io.riskplatform.monolith.repository.SecretsBootstrap;
import io.riskplatform.monolith.repository.SqsDecisionPublisher;
import io.riskplatform.monolith.repository.ValkeyIdempotencyStore;
import io.riskplatform.monolith.usecase.EvaluateRiskUseCase;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;

/**
 * Manual DI wiring for the single-JVM monolith.
 *
 * <p>Contrast with vertx-layer-as-pod-eventbus where each layer lives in a separate JVM
 * and wiring is done per-process. Here, all layers are wired together in one place
 * and deployed as verticles in the same Vert.x instance.
 *
 * <p>Order of initialization:
 * <ol>
 *   <li>Secrets resolution (Moto / OpenBao / env fallback).
 *   <li>Database pool creation + DDL migration.
 *   <li>Repository adapters (Postgres, Valkey, Kafka, S3, SQS).
 *   <li>Use case verticle.
 *   <li>HTTP server verticle.
 * </ol>
 */
public class ApplicationFactory {

    private static final Logger log = LoggerFactory.getLogger(ApplicationFactory.class);

    private final Vertx vertx;

    public ApplicationFactory(Vertx vertx) {
        this.vertx = vertx;
    }

    public Future<Void> deploy() {
        // Step 1: resolve secrets, then bootstrap DB, then wire everything
        return vertx.executeBlocking(() -> {
            String dbPassword = SecretsBootstrap.resolveDbPassword();
            String dbHost     = System.getenv().getOrDefault("PG_HOST",     "postgres");
            int    dbPort     = Integer.parseInt(System.getenv().getOrDefault("PG_PORT", "5432"));
            String dbName     = System.getenv().getOrDefault("PG_DATABASE", "riskplatform");
            String dbUser     = System.getenv().getOrDefault("PG_USER",     "riskplatform");
            String jdbcUrl    = "jdbc:postgresql://" + dbHost + ":" + dbPort + "/" + dbName;
            return new DbCredentials(jdbcUrl, dbUser, dbPassword);
        }).compose(creds -> {
            // Step 2: Bootstrap DB schema
            return DbBootstrap.init(creds.url(), creds.user(), creds.password());
        }).compose(ignored -> {
            // Step 3: Build adapters
            PostgresFeatureRepository featureRepo = new PostgresFeatureRepository();
            ValkeyIdempotencyStore    idempotency  = new ValkeyIdempotencyStore();
            KafkaDecisionPublisher    kafka        = new KafkaDecisionPublisher();
            S3AuditPublisher          s3           = new S3AuditPublisher(vertx);
            SqsDecisionPublisher      sqs          = new SqsDecisionPublisher(vertx);

            // Step 4: Deploy use-case verticle
            EvaluateRiskUseCase usecaseVerticle = new EvaluateRiskUseCase(
                featureRepo, idempotency, kafka, s3, sqs);

            // Step 5: Deploy HTTP verticle
            HttpServerVerticle httpVerticle = new HttpServerVerticle();

            return vertx.deployVerticle(usecaseVerticle)
                .compose(id -> {
                    log.info("[vertx-monolith-inprocess] EvaluateRiskUseCase deployed: {}", id);
                    return vertx.deployVerticle(httpVerticle);
                })
                .compose(id -> {
                    log.info("[vertx-monolith-inprocess] HttpServerVerticle deployed: {}", id);
                    return Future.<Void>succeededFuture();
                });
        });
    }

    private record DbCredentials(String url, String user, String password) {}
}
