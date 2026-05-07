package com.naranjax.monolith.integration;

import com.naranjax.distributed.shared.FeatureSnapshot;
import com.naranjax.monolith.repository.DbBootstrap;
import com.naranjax.monolith.repository.PostgresFeatureRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test: PostgresFeatureRepository against a real Postgres container.
 */
@Testcontainers
class PostgresFeatureRepositoryIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("naranjax")
        .withUsername("naranjax")
        .withPassword("naranjax");

    static PostgresFeatureRepository repository;

    @BeforeAll
    static void setup() throws SQLException {
        DbBootstrap.doInit(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        repository = new PostgresFeatureRepository(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    @Test
    void findByCustomerId_returnsSeededCustomer() throws SQLException {
        FeatureSnapshot snap = repository.findByCustomerId("c-1");
        assertThat(snap.customerId()).isEqualTo("c-1");
        assertThat(snap.riskScore()).isEqualTo(0.82);
        assertThat(snap.country()).isEqualTo("AR");
    }

    @Test
    void findByCustomerId_returnsDefaultSnapshot_forUnknownCustomer() throws SQLException {
        FeatureSnapshot snap = repository.findByCustomerId("c-unknown-xyz");
        assertThat(snap.customerId()).isEqualTo("c-unknown-xyz");
        assertThat(snap.riskScore()).isEqualTo(0.5);
        assertThat(snap.country()).isEqualTo("UNKNOWN");
    }

    @Test
    void findByCustomerId_returnsHighRiskCustomer() throws SQLException {
        FeatureSnapshot snap = repository.findByCustomerId("c-4");
        assertThat(snap.riskScore()).isEqualTo(0.95);
        assertThat(snap.chargebackCount90d()).isEqualTo(3);
        assertThat(snap.knownDevice()).isFalse();
    }

    @Test
    void findByCustomerId_returnsYoungCustomer() throws SQLException {
        // c-3 has customerAgeDays=20 — used to trigger NewDeviceYoungCustomerRule
        FeatureSnapshot snap = repository.findByCustomerId("c-3");
        assertThat(snap.customerAgeDays()).isEqualTo(20);
        assertThat(snap.knownDevice()).isFalse();
    }

    @Test
    void schema_isIdempotent_runningBootstrapTwice() throws SQLException {
        // Running init again must not throw
        DbBootstrap.doInit(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        FeatureSnapshot snap = repository.findByCustomerId("c-2");
        assertThat(snap.riskScore()).isEqualTo(0.30);
    }
}
