package com.bharathi.vulntrack.web.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /api/findings/{id}/accept-risk.
 * acceptedBy is written down as given. It is not verified: the API key identifies the caller's
 * role, not the person.
 */
public record AcceptRiskRequest(
        @NotBlank @Size(max = 500) String reason,
        @NotBlank @Size(max = 100) String acceptedBy,
        @NotNull Instant expiresAt
) {
}
