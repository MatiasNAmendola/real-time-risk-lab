package com.naranjax.interview.risk.cmd;



/**
 * Bootstrap entry point — equivalente a cmd/main.go (enterprise Go layout).
 * Delega a CliRunner (inbound adapter). El wiring de dependencias
 * vive en {@link com.naranjax.interview.risk.config.RiskApplicationFactory}.
 */
public final class RiskApplication {
    public static void main(String[] args) throws Exception {
        CliRunner.main(args);
    }
}
