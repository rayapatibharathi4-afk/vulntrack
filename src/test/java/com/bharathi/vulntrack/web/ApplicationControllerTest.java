package com.bharathi.vulntrack.web;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bharathi.vulntrack.config.ApiKeyAuthFilter;
import com.bharathi.vulntrack.config.SecurityConfig;
import com.bharathi.vulntrack.service.ApplicationService;
import com.bharathi.vulntrack.service.ConflictException;
import com.bharathi.vulntrack.web.dto.ApplicationResponse;

// A dummy key for this test only, not a real secret
@WebMvcTest(ApplicationController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "vulntrack.security.api-key=test-only-key",
        "vulntrack.security.read-api-key=test-only-read-key"
})
class ApplicationControllerTest {

    private static final String KEY = "test-only-key";
    private static final String READ_KEY = "test-only-read-key";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApplicationService service;

    @Test
    void createReturns201WithLocationHeader() throws Exception {
        when(service.create(any())).thenReturn(new ApplicationResponse(
                1L, "payments-service", null, "payments",
                Instant.parse("2026-11-11T10:00:00Z")));

        mockMvc.perform(post("/api/applications")
                        .header(ApiKeyAuthFilter.HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "payments-service", "ownerTeam": "payments"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/applications/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("payments-service"));
    }

    @Test
    void createRejectsInvalidNameWithoutCallingService() throws Exception {
        mockMvc.perform(post("/api/applications")
                        .header(ApiKeyAuthFilter.HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Payments Service"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.errors.name").exists());

        verifyNoInteractions(service);
    }

    @Test
    void createReturns409WhenNameAlreadyExists() throws Exception {
        when(service.create(any()))
                .thenThrow(new ConflictException("Application 'payments-service' already exists"));

        mockMvc.perform(post("/api/applications")
                        .header(ApiKeyAuthFilter.HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "payments-service"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Application 'payments-service' already exists"));
    }

    @Test
    void writeWithoutKeyIsRejectedAsProblemDetailsAndNeverReachesTheService() throws Exception {
        mockMvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "payments-service"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"));

        verifyNoInteractions(service);
    }

    @Test
    void readWithReadKeyIsAllowed() throws Exception {
        when(service.list(any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/applications").header(ApiKeyAuthFilter.HEADER, READ_KEY))
                .andExpect(status().isOk());
    }

    @Test
    void readWithoutKeyIsRejected() throws Exception {
        mockMvc.perform(get("/api/applications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"));

        verifyNoInteractions(service);
    }

    @Test
    void readOnlyKeyCannotWrite() throws Exception {
        mockMvc.perform(post("/api/applications")
                        .header(ApiKeyAuthFilter.HEADER, READ_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "payments-service"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));

        verifyNoInteractions(service);
    }

    @Test
    void ingestKeyCanAlsoRead() throws Exception {
        when(service.list(any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/applications").header(ApiKeyAuthFilter.HEADER, KEY))
                .andExpect(status().isOk());
    }

    @Test
    void writeWithWrongKeyIsRejected() throws Exception {
        mockMvc.perform(post("/api/applications")
                        .header(ApiKeyAuthFilter.HEADER, "not-the-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "payments-service"}
                                """))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }
}