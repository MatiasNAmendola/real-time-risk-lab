package io.riskplatform.vertx.common;

import io.riskplatform.vertx.controller.ControllerPod;
import io.riskplatform.vertx.repository.RepositoryPod;
import io.riskplatform.vertx.usecase.UsecasePod;
import io.vertx.core.Vertx;

public final class PodMain {
    public static void main(String[] args) {
        if (args.length != 1) throw new IllegalArgumentException("usage: java -jar app.jar controller|usecase|repository");
        var vertx = Vertx.vertx();
        switch (args[0]) {
            case "controller" -> vertx.deployVerticle(new ControllerPod());
            case "usecase" -> vertx.deployVerticle(new UsecasePod());
            case "repository" -> vertx.deployVerticle(new RepositoryPod());
            default -> throw new IllegalArgumentException("unknown pod: " + args[0]);
        }
    }
}
