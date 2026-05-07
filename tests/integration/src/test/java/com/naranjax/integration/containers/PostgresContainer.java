package com.naranjax.integration.containers;

import org.testcontainers.containers.PostgreSQLContainer;

public final class PostgresContainer {

    private static final String IMAGE = "postgres:16-alpine";

    private PostgresContainer() {}

    @SuppressWarnings("resource")
    public static PostgreSQLContainer<?> create() {
        return new PostgreSQLContainer<>(IMAGE)
                .withDatabaseName("naranjax_test")
                .withUsername("test_user")
                .withPassword("test_pass");
    }
}
