package com.flowdesk.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowdesk.testsupport.WebIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Proves the OpenAPI schema and Swagger UI are actually reachable
 * <em>without authentication</em> - the {@code permitAll} rule
 * {@code SecurityConfig} adds for these paths - and that
 * {@code OpenApiConfig}'s metadata/security scheme show up in the
 * generated schema. Both are disabled entirely in the {@code prod}
 * profile (see {@code application-prod.yml}), which isn't exercised by
 * this test class (it runs on {@code test}, where springdoc's defaults
 * leave both enabled).
 */
@WebIntegrationTest
class OpenApiIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocs_reachableWithoutAuthentication_andDescribesTheApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("FlowDesk API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                // Spot-check one endpoint from each of a couple of modules
                // made it into the generated schema, rather than asserting
                // every path - the point is proving generation is wired up
                // end-to-end, not re-documenting the whole API here.
                .andExpect(jsonPath("$.paths./api/auth/login").exists())
                .andExpect(jsonPath("$.paths./api/tickets").exists());
    }

    @Test
    void swaggerUi_reachableWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
