package com.flowdesk.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Every {@link IntegrationTest} boots against these three ephemeral
 * containers - Postgres, Kafka, and Redis - never the shared
 * {@code docker-compose.yml} services a developer runs locally with
 * {@code ./mvnw spring-boot:run}. {@code @ServiceConnection} does all the
 * wiring: Spring Boot auto-detects each container's type (from its image)
 * and injects the matching {@code spring.datasource.*}/
 * {@code spring.kafka.bootstrap-servers}/{@code spring.data.redis.*}
 * properties itself, at whatever host port Docker happens to assign -
 * nothing here or in {@code application-test.yml} hardcodes a port the
 * way {@code application-dev.yml} does for the docker-compose services.
 *
 * <p><b>Why this replaced running tests against docker-compose:</b> three
 * real problems that came up earlier in this project turn into structural
 * non-issues once every test run gets its own fresh containers instead of
 * sharing one long-lived instance: state (Redis rate-limit counters, cache
 * entries, database rows) leaking between separate {@code mvn verify}
 * invocations, a developer needing to remember to run
 * {@code docker compose up} before the test suite at all, and CI needing
 * that same manual setup step reproduced in a pipeline.
 *
 * <p><b>Container reuse across test classes.</b> These beans aren't
 * static/shared explicitly - reuse instead comes from Spring's test
 * {@code ApplicationContext} caching: every test class that uses
 * {@link IntegrationTest} (or {@link WebIntegrationTest}) imports this
 * exact same configuration with the exact same active profile, so most of
 * them share one cached context - and therefore one set of already-running
 * containers - for the whole test run. A class that adds its own
 * {@code @TestPropertySource} (e.g. {@code AuthRateLimitFilterTest}) gets
 * a different context key and therefore its own fresh containers - slower
 * for that one class, but correct, and not a new trade-off Testcontainers
 * introduced (the same context-splitting already happened against the
 * shared dev services).
 */
@TestConfiguration(proxyBeanMethods = false)
public class ContainersConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:16-alpine");
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));
    }

    /**
     * A plain {@code GenericContainer}, not a dedicated Testcontainers
     * Redis module (there isn't an official one) - Spring Boot's
     * {@code @ServiceConnection} support recognizes a generic container
     * running a {@code redis} image by name and wires
     * {@code spring.data.redis.*} from it the same way it would from a
     * purpose-built module.
     */
    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    }
}
