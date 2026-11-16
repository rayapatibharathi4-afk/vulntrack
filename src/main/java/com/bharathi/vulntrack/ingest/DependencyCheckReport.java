package com.bharathi.vulntrack.ingest;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The parts of an OWASP Dependency-Check JSON report that VulnTrack uses. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DependencyCheckReport(ProjectInfo projectInfo, List<ReportDependency> dependencies) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProjectInfo(String name, String reportDate) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReportDependency(String fileName,
                                   List<ReportPackage> packages,
                                   List<ReportVulnerability> vulnerabilities) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReportPackage(String id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReportVulnerability(String source,
                                      String name,
                                      String severity,
                                      Cvss cvssv3,
                                      String description) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Cvss(BigDecimal baseScore) {
    }
}