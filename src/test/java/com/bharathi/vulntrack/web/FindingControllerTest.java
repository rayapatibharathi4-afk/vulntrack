package com.bharathi.vulntrack.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.bharathi.vulntrack.config.ApiKeyAuthFilter;
import com.bharathi.vulntrack.config.SecurityConfig;
import com.bharathi.vulntrack.domain.FindingStatus;
import com.bharathi.vulntrack.service.FindingService;
import com.bharathi.vulntrack.web.dto.FindingResponse;

// Dummy keys for this test only, not real secrets
@WebMvcTest(FindingController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "vulntrack.security.api-key=test-only-ingest",
        "vulntrack.security.read-api-key=test-only-read",
        "vulntrack.security.risk-api-key=test-only-risk"
})
class FindingControllerTest {

    private static final String ACCEPT_BODY = """
            {"reason": "Only reachable from the internal network",
             "acceptedBy": "j.doe",
             "expiresAt": "2099-01-01T00:00:00Z"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FindingService service;

    @Test
    void riskKeyCanAcceptARisk() throws Exception {
        when(service.acceptRisk(eq(7L), any())).thenReturn(new FindingResponse(
                7L, "payments-service", null, null, null, null, null, null, null,
                FindingStatus.ACCEPTED_RISK, null, null, null, null, null, false));

        mockMvc.perform(post("/api/findings/7/accept-risk")
                        .header(ApiKeyAuthFilter.HEADER, "test-only-risk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ACCEPT_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED_RISK"));

        verify(service).acceptRisk(eq(7L), any());
    }

    @Test
    void ingestKeyCannotAcceptARisk() throws Exception {
        mockMvc.perform(post("/api/findings/7/accept-risk")
                        .header(ApiKeyAuthFilter.HEADER, "test-only-ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ACCEPT_BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void readKeyCannotAcceptARisk() throws Exception {
        mockMvc.perform(post("/api/findings/7/accept-risk")
                        .header(ApiKeyAuthFilter.HEADER, "test-only-read")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ACCEPT_BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void acceptingWithoutAKeyIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/findings/7/accept-risk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ACCEPT_BODY))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    @Test
    void aMissingReasonIsRejectedBeforeTheServiceRuns() throws Exception {
        mockMvc.perform(post("/api/findings/7/accept-risk")
                        .header(ApiKeyAuthFilter.HEADER, "test-only-risk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"acceptedBy": "j.doe", "expiresAt": "2099-01-01T00:00:00Z"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reason").exists());

        verifyNoInteractions(service);
    }
}
