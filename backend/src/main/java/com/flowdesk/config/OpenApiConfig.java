package com.flowdesk.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI/Swagger UI (springdoc), at {@code /swagger-ui.html} and
 * {@code /v3/api-docs}. Both are permitted unauthenticated in
 * {@code SecurityConfig} - safe unconditionally (rather than only
 * permitting them in non-prod profiles) because they're disabled entirely
 * in {@code application-prod.yml} (`springdoc.api-docs.enabled` /
 * `springdoc.swagger-ui.enabled: false`); springdoc doesn't even register
 * the underlying controllers when disabled, so the permitted paths simply
 * 404 in prod rather than being reachable. Publishing a full API schema
 * (every endpoint, every DTO shape) is a reasonable convenience for local
 * development and this project's own documentation, but unnecessary
 * public surface area in a real deployment.
 *
 * <p>Relies on springdoc's automatic generation from each controller's
 * method signatures, path/query parameters, and the Bean Validation
 * annotations already on every request DTO - not hand-annotated with
 * {@code @Operation}/{@code @ApiResponse} on every endpoint. That's a
 * deliberate scope decision: this project's README already documents the
 * API in depth, and exhaustively annotating close to 40 endpoints for
 * marginally better generated descriptions wasn't judged worth the
 * boilerplate. Each controller does carry a {@code @Tag} so Swagger UI
 * groups endpoints by module instead of listing all of them flat.
 *
 * <p>The Bearer JWT security scheme registered below is what makes
 * Swagger UI's "Authorize" button work for trying a protected endpoint
 * directly from the browser - paste an access token once, and it's sent
 * on every subsequent "Try it out" call.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI flowDeskOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("FlowDesk API")
                        .description("Multi-tenant support and workflow management platform.")
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME_NAME, new SecurityScheme()
                                .name(BEARER_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
