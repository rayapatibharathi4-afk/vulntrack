package com.bharathi.vulntrack.web.dto;

import java.time.Instant;

import com.bharathi.vulntrack.domain.Application;

public record ApplicationResponse(
        Long id,
        String name,
        String repoUrl,
        String ownerTeam,
        Instant createdAt
) {
    public static ApplicationResponse from(Application app) {
        return new ApplicationResponse(
                app.getId(), app.getName(), app.getRepoUrl(),
                app.getOwnerTeam(), app.getCreatedAt());
    }
}