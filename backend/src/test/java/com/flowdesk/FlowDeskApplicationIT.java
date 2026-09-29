package com.flowdesk;

import com.flowdesk.testsupport.IntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * Verifies the Spring application context loads successfully, against the
 * ephemeral Postgres/Kafka/Redis containers {@code @IntegrationTest} spins
 * up (Phase 10) - not a developer's local Docker state, and runnable in
 * CI without any prior setup. {@code ddl-auto} is intentionally
 * {@code validate} rather than relying on an in-memory database that
 * would mask real schema/mapping mismatches.
 */
@IntegrationTest
class FlowDeskApplicationTests {

    @Test
    void contextLoads() {
        // Intentionally empty: a failure to load the ApplicationContext
        // (bad configuration, missing beans, unreachable datasource, an
        // invalid Flyway migration) will fail this test on its own.
    }
}
