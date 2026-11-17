package com.bharathi.vulntrack.nvd;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The parts of an NVD CVE API 2.0 response that VulnTrack uses. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NvdResponse(int totalResults, List<Vulnerability> vulnerabilities) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Vulnerability(NvdCve cve) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NvdCve(String id, String published, List<Description> descriptions, Metrics metrics) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Description(String lang, String value) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Metrics(List<CvssMetric> cvssMetricV31, List<CvssMetric> cvssMetricV30) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CvssMetric(String type, CvssData cvssData) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CvssData(BigDecimal baseScore, String baseSeverity) {
    }
}