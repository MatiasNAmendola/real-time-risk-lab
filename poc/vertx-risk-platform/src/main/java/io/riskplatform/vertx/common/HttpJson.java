package io.riskplatform.vertx.common;

import io.vertx.ext.web.RoutingContext;

public final class HttpJson {
    private HttpJson() {}

    public static void ok(RoutingContext ctx, Object body) {
        ctx.response().putHeader("content-type", "application/json").end(Json.encode(body));
    }

    public static void created(RoutingContext ctx, Object body) {
        ctx.response().setStatusCode(201).putHeader("content-type", "application/json").end(Json.encode(body));
    }

    public static void error(RoutingContext ctx, int status, String code, String message) {
        ctx.response().setStatusCode(status).putHeader("content-type", "application/json")
                .end(Json.encode(new ErrorResponse(code, message)));
    }

    public record ErrorResponse(String code, String message) {}
}
