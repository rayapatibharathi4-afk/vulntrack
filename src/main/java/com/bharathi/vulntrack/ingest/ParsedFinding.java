package com.bharathi.vulntrack.ingest;

import java.math.BigDecimal;

import com.bharathi.vulntrack.domain.Ecosystem;
import com.bharathi.vulntrack.domain.Severity;

/** One "package version X has CVE Y" fact extracted from a scan report. */
public record ParsedFinding(
        Ecosystem ecosystem,
        String packageName,
        String version,
        String cveId,
        Severity severity,
        BigDecimal cvssScore,
        String description
) {
}