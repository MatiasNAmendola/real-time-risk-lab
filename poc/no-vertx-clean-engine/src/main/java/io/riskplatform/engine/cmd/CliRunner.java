package io.riskplatform.engine.cmd;

import io.riskplatform.engine.domain.context.ExecutionContext;
import io.riskplatform.engine.application.dto.EvaluateRiskRequestDTO;
import io.riskplatform.engine.application.mapper.RiskDecisionMapper;
import io.riskplatform.engine.config.RiskApplicationFactory;
import io.riskplatform.engine.domain.entity.CorrelationId;
import io.riskplatform.engine.infrastructure.repository.log.ConsoleStructuredLogger;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Inbound adapter — CLI controller.
 * Equivalente a infrastructure/controllers/ (enterprise Go layout).
 */
public final class CliRunner {
    public static void main(String[] args) throws Exception {
        try (var app = new RiskApplicationFactory()) {
            var useCase = app.evaluateRiskUseCase();
            var requests = List.of(
                    request("tx-001", "user-123", 1_200, false, "retry-key-001"),
                    request("tx-002", "user-456", 70_000, false, "retry-key-002"),
                    request("tx-003", "user-789", 8_500, true, "retry-key-003"),
                    request("tx-003", "user-789", 8_500, true, "retry-key-003"),
                    request("tx-004", "user-999", 15_000, false, "retry-key-004")
            );

            for (var dto : requests) {
                var request = RiskDecisionMapper.toDomain(dto);
                var context = new ExecutionContext(
                        new CorrelationId(dto.correlationId()),
                        new ConsoleStructuredLogger().with("adapter", "cli")
                );
                var response = useCase.evaluate(context, request, Duration.ofMillis(300));
                app.outboxRelay().flushAsync();
                System.out.println("\n=== Decision ===");
                System.out.println(RiskDecisionMapper.toDTO(response));
            }
        }
    }

    private static EvaluateRiskRequestDTO request(String tx, String user, long cents, boolean newDevice, String idempotencyKey) {
        return new EvaluateRiskRequestDTO(
                tx,
                user,
                cents,
                "ARS",
                newDevice,
                UUID.randomUUID().toString(),
                idempotencyKey
        );
    }
}
