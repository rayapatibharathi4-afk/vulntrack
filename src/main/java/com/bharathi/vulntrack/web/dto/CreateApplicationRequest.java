package com.bharathi.vulntrack.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateApplicationRequest(

        @NotBlank
        @Size(max = 100)
        @Pattern(regexp = "^[a-z0-9][a-z0-9-]*$",
                 message = "must be lowercase letters, digits and hyphens")
        String name,

        @Size(max = 500)
        String repoUrl,

        @Size(max = 100)
        String ownerTeam
) {
}