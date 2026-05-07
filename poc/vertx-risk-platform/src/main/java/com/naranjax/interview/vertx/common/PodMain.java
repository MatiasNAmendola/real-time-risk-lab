package com.naranjax.interview.vertx.common;

import com.naranjax.interview.vertx.controller.ControllerPod;
import com.naranjax.interview.vertx.repository.RepositoryPod;
import com.naranjax.interview.vertx.usecase.UsecasePod;
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
