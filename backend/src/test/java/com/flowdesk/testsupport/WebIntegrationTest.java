package com.flowdesk.testsupport;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

/** {@link IntegrationTest} plus {@code @AutoConfigureMockMvc}, for the majority of integration tests that drive the app through MockMvc. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@IntegrationTest
@AutoConfigureMockMvc
public @interface WebIntegrationTest {
}
