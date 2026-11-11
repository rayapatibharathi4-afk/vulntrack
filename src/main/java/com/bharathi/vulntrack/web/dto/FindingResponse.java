package com.bharathi.vulntrack.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.bharathi.vulntrack.domain.Cve;
import com.bharathi.vulntrack.domain.Dependency;
import com.bharathi.vulntrack.domain.Ecosystem;
import com.bharathi.vulntrack.domain.Finding;
import com.bharathi.vulntrack.domain.FindingStatus;
import com.bharathi.vulntrack.domain.Severity;

public record FindingResponse(
        Long id,
        String application,
        Ecosystem ecosystem,
        String dependency,
        String version,
        String cveId,
        Severity severity,
        BigDecimal cvssScore,
        FindingStatus status,
        String assignee,
        Instant firstSeenAt,
        Instant lastSeenAt,
        Instant slaDueAt,
        Instant fixedAt,
        boolean overdue
) {
    public static FindingResponse from(Finding finding, Instant now) {
        Dependency dep = finding.getDependency();
        Cve cve = finding.getCve();
        return new FindingResponse(
                finding.getId(),
                dep.getApplication().getName(),
                dep.getEcosystem(),
                dep.getName(),
                dep.getVersion(),
                cve.getId(),
                cve.getSeverity(),
                cve.getCvssScore(),
                finding.getStatus(),
                finding.getAssignee(),
                finding.getFirstSeenAt(),
                finding.getLastSeenAt(),
                finding.getSlaDueAt(),
                finding.getFixedAt(),
                finding.isOverdue(now));
    }
}