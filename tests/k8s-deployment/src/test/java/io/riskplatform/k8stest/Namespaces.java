package io.riskplatform.k8stest;

import java.util.UUID;

/** Generates ephemeral, unique namespaces so concurrent test runs do not collide. */
public final class Namespaces {
    private Namespaces() {}

    public static String ephemeral() {
        return "k8stest-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
