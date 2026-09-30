package com.flowdesk.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowdesk.testsupport.WebIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@WebIntegrationTest
class CorrelationIdFilterIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void noIncomingHeader_generatesAndReturnsAFreshCorrelationId() throws Exception {
        var result = mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists(CorrelationIdFilter.HEADER_NAME))
                .andReturn();

        String correlationId = result.getResponse().getHeader(CorrelationIdFilter.HEADER_NAME);
        assertThat(correlationId).isNotBlank();
        assertThat(UUID.fromString(correlationId)).isNotNull();
    }

    @Test
    void incomingHeader_isEchoedBackUnchanged() throws Exception {
        String incoming = "caller-supplied-id-" + UUID.randomUUID();

        mockMvc.perform(get("/actuator/health").header(CorrelationIdFilter.HEADER_NAME, incoming))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, incoming));
    }

    @Test
    void twoRequestsWithNoIncomingHeader_getDifferentCorrelationIds() throws Exception {
        var first = mockMvc.perform(get("/actuator/health")).andReturn();
        var second = mockMvc.perform(get("/actuator/health")).andReturn();

        String firstId = first.getResponse().getHeader(CorrelationIdFilter.HEADER_NAME);
        String secondId = second.getResponse().getHeader(CorrelationIdFilter.HEADER_NAME);
        assertThat(firstId).isNotEqualTo(secondId);
    }
}
