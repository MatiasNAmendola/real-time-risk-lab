package io.riskplatform.vertx.common;

import io.vertx.ext.web.RoutingContext;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public final class PodSecurity {
    public static final String CONTROLLER_TO_USECASE_TOKEN = "controller-risk-evaluate-token";
    public static final String USECASE_TO_REPOSITORY_TOKEN = "usecase-repository-rw-token";

    private PodSecurity() {}

    public static void requireToken(RoutingContext ctx, String expectedToken, String requiredScope) {
        var token = ctx.request().getHeader("x-pod-token");
        var scopes = parseScopes(ctx.request().getHeader("x-pod-scopes"));
        if (!expectedToken.equals(token) || !scopes.contains(requiredScope)) {
            HttpJson.error(ctx, 403, "FORBIDDEN", "pod token or scope not allowed for " + requiredScope);
            return;
        }
        ctx.next();
    }

    private static Set<String> parseScopes(String header) {
        if (header == null || header.isBlank()) return Set.of();
        return Arrays.stream(header.split(",")).map(String::trim).filter(s -> !s.isBlank()).collect(Collectors.toSet());
    }
}
