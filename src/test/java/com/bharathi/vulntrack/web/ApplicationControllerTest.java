package com.bharathi.vulntrack.web;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bharathi.vulntrack.service.ApplicationService;
import com.bharathi.vulntrack.service.ConflictException;
import com.bharathi.vulntrack.web.dto.ApplicationResponse;

@WebMvcTest(ApplicationController.class)
class ApplicationControllerTest {

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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "payments-service"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Application 'payments-service' already exists"));
    }
}