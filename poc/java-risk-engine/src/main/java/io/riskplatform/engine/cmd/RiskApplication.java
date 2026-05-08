package io.riskplatform.engine.cmd;



/**
 * Bootstrap entry point — equivalente a cmd/main.go (enterprise Go layout).
 * Delega a CliRunner (inbound adapter). El wiring de dependencias
 * vive en {@link io.riskplatform.engine.config.RiskApplicationFactory}.
 */
public final class RiskApplication {
    public static void main(String[] args) throws Exception {
        CliRunner.main(args);
    }
}
