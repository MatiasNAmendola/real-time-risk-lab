package io.riskplatform.engine.config;

import io.riskplatform.engine.domain.context.ExecutionContext;
import io.riskplatform.engine.application.usecase.risk.EvaluateTransactionRiskService;
import io.riskplatform.engine.domain.entity.CorrelationId;
import io.riskplatform.engine.domain.repository.AuditEventPublisher;
import io.riskplatform.engine.domain.repository.SecretsProvider;
import io.riskplatform.engine.domain.rule.HighAmountRule;
import io.riskplatform.engine.domain.rule.NewDeviceYoungCustomerRule;
import io.riskplatform.engine.domain.service.*;
import io.riskplatform.engine.domain.usecase.EvaluateRiskUseCase;
import io.riskplatform.engine.infrastructure.repository.audit.NoOpAuditEventPublisher;
import io.riskplatform.engine.infrastructure.repository.audit.S3AuditEventPublisher;
import io.riskplatform.engine.infrastructure.repository.event.*;
import io.riskplatform.engine.infrastructure.repository.feature.InMemoryFeatureProvider;
import io.riskplatform.engine.infrastructure.repository.idempotency.InMemoryDecisionIdempotencyStore;
import io.riskplatform.engine.infrastructure.repository.log.ConsoleStructuredLogger;
import io.riskplatform.engine.infrastructure.repository.ml.FakeRiskModelScorer;
import io.riskplatform.engine.infrastructure.repository.persistence.*;
import io.riskplatform.engine.infrastructure.repository.secrets.EnvSecretsProvider;
import io.riskplatform.engine.infrastructure.repository.secrets.MotoSecretsManagerProvider;
import io.riskplatform.engine.infrastructure.resilience.CircuitBreaker;
import io.riskplatform.engine.infrastructure.time.SystemClockAdapter;
import io.riskplatform.rules.audit.RulesAuditTrail;
import io.riskplatform.rules.config.RulesConfigLoader;
import io.riskplatform.rules.engine.RuleEngine;
import io.riskplatform.rules.engine.RuleEngineImpl;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * Manual DI wiring — equivalente a cmd/main.go (enterprise Go layout).
 * Construye el grafo de dependencias sin framework de inyección.
 *
 * AWS integration (graceful degradation):
 *   - AuditEventPublisher: uses S3AuditEventPublisher when AWS_ENDPOINT_URL_S3 is set
 *     (Phase 2 — requires AWS SDK v2). Falls back to NoOpAuditEventPublisher otherwise.
 *   - SecretsProvider: uses MotoSecretsManagerProvider when AWS_ENDPOINT_URL_SECRETSMANAGER
 *     is set (Phase 2). Falls back to EnvSecretsProvider (reads env vars) otherwise.
 */
public final class RiskApplicationFactory implements AutoCloseable {
    private final InMemoryOutboxRepository outboxRepository;
    private final OutboxRelay outboxRelay;
    private final EvaluateRiskUseCase evaluateRiskUseCase;
    private final AuditEventPublisher auditEventPublisher;
    private final SecretsProvider secretsProvider;
    private final RuleEngine ruleEngine;
    private final RulesAuditTrail rulesAuditTrail;

    public RiskApplicationFactory() {
        this(false);
    }

    /** @param silent when true, all internal logging is suppressed — useful for benchmarks. */
    public RiskApplicationFactory(boolean silent) {
        var bootstrapContext = new ExecutionContext(
                new CorrelationId("bootstrap-" + UUID.randomUUID()),
                new ConsoleStructuredLogger(silent).with("service", "risk-decision-poc")
        );

        // AWS adapter wiring — graceful degradation when env vars are absent
        // TODO(phase-2): S3AuditEventPublisher and MotoSecretsManagerProvider require AWS SDK v2.
        //   Add Gradle build file with software.amazon.awssdk:s3:2.29.23,
        //   software.amazon.awssdk:secretsmanager:2.29.23, url-connection-client:2.29.23.
        //   Then these adapters will compile and the wiring below activates automatically.
        String s3Endpoint       = System.getenv("AWS_ENDPOINT_URL_S3");
        String secretsEndpoint  = System.getenv("AWS_ENDPOINT_URL_SECRETSMANAGER");
        String auditBucket      = System.getenv().getOrDefault("RISK_AUDIT_BUCKET", "risk-audit");

        this.auditEventPublisher = (s3Endpoint != null)
                ? new S3AuditEventPublisher(s3Endpoint, auditBucket)
                : new NoOpAuditEventPublisher();

        this.secretsProvider = (secretsEndpoint != null)
                ? new MotoSecretsManagerProvider(secretsEndpoint)
                : new EnvSecretsProvider();

        this.outboxRepository = new InMemoryOutboxRepository();
        this.outboxRelay = new OutboxRelay(outboxRepository, Executors.newVirtualThreadPerTaskExecutor());
        var eventPublisher = new OutboxDecisionEventPublisher(outboxRepository, bootstrapContext);

        this.evaluateRiskUseCase = new EvaluateTransactionRiskService(
                new RuleBasedDecisionPolicy(List.of(new HighAmountRule(), new NewDeviceYoungCustomerRule())),
                new ScoreDecisionPolicy(),
                new FallbackDecisionPolicy(),
                new InMemoryFeatureProvider(),
                new FakeRiskModelScorer(),
                new CircuitBreaker(3, Duration.ofSeconds(3)),
                eventPublisher,
                new InMemoryDecisionIdempotencyStore(),
                new InMemoryRiskDecisionRepository(),
                new InMemoryTransactionManager(),
                new SystemClockAdapter()
        );

        // Bootstrap declarative rules engine from RULES_CONFIG_PATH env var
        String rulesConfigPath = System.getenv().getOrDefault(
                "RULES_CONFIG_PATH",
                "examples/rules-config/v1/rules.yaml");
        this.rulesAuditTrail = new RulesAuditTrail();
        var loader = new RulesConfigLoader();
        io.riskplatform.rules.config.RulesConfig rulesConfig;
        try {
            rulesConfig = loader.load(rulesConfigPath);
        } catch (Exception e) {
            // Fallback to minimal in-memory config if file not found (e.g., in tests)
            rulesConfig = buildMinimalConfig();
        }
        this.ruleEngine = new RuleEngineImpl(rulesConfig, rulesAuditTrail);
    }

    public EvaluateRiskUseCase evaluateRiskUseCase() { return evaluateRiskUseCase; }
    public OutboxRelay outboxRelay() { return outboxRelay; }
    public InMemoryOutboxRepository outboxRepository() { return outboxRepository; }
    public AuditEventPublisher auditEventPublisher() { return auditEventPublisher; }
    public SecretsProvider secretsProvider() { return secretsProvider; }
    public RuleEngine ruleEngine() { return ruleEngine; }
    public RulesAuditTrail rulesAuditTrail() { return rulesAuditTrail; }

    @Override public void close() throws Exception { outboxRelay.close(); }

    private static io.riskplatform.rules.config.RulesConfig buildMinimalConfig() {
        return new io.riskplatform.rules.config.RulesConfig(
                "0.0.0", "sha256:fallback", null, "system", "dev",
                "worst_case_with_allowlist_override", 5000, "REVIEW",
                List.of(), List.of());
    }
}
