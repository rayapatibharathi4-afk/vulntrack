package com.bharathi.vulntrack.nvd;

import java.math.BigDecimal;
import java.time.Instant;

import com.bharathi.vulntrack.domain.Severity;

/** Authoritative CVE details from NVD, in VulnTrack's terms. */
public record CveDetails(
        String cveId,
        Severity severity,
        BigDecimal cvssScore,
        String description,
        Instant publishedAt
) {
}