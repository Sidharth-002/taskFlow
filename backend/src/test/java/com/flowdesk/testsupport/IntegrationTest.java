package com.flowdesk.testsupport;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Replaces the {@code @SpringBootTest @ActiveProfiles("dev")} pair every
 * integration test used before Phase 10 - {@code @Import(ContainersConfig.class)}
 * is what actually switches the target from the shared docker-compose
 * services to ephemeral Testcontainers (see its Javadoc), and
 * {@code "test"} is its own profile (`application-test.yml`), not a reuse
 * of {@code "dev"}, since a couple of settings (the JWT secret fallback,
 * generous rate limits) need their own test-only values independent of
 * whatever `application-dev.yml` happens to have.
 *
 * <p>A test class still adds its own {@code @AutoConfigureMockMvc}/
 * {@code @Transactional}/{@code @TestPropertySource} as needed - this
 * annotation only replaces the two lines every one of them had in common.
 * {@link WebIntegrationTest} additionally bundles
 * {@code @AutoConfigureMockMvc} for the (majority) of test classes that
 * want both.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@Import(ContainersConfig.class)
@ActiveProfiles("test")
public @interface IntegrationTest {
}
