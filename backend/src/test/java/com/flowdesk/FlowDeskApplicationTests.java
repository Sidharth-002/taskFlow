package com.flowdesk;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies the Spring application context loads successfully.
 *
 * <p>Requires a reachable PostgreSQL instance matching the "dev" profile
 * (e.g. via {@code docker compose up -d postgres}), since {@code ddl-auto}
 * is intentionally set to {@code validate} rather than relying on an
 * in-memory database that would mask real schema/mapping mismatches.
 *
 * <p>This is replaced by a Testcontainers-backed base test class in
 * Phase 10, which removes the dependency on a developer's local Docker
 * state and makes the suite runnable in CI without prior setup.
 */
@SpringBootTest
@ActiveProfiles("dev")
class FlowDeskApplicationTests {

    @Test
    void contextLoads() {
        // Intentionally empty: a failure to load the ApplicationContext
        // (bad configuration, missing beans, unreachable datasource, an
        // invalid Flyway migration) will fail this test on its own.
    }
}
