package com.bharathi.vulntrack.nvd;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.springframework.web.client.RestClient;

import com.bharathi.vulntrack.domain.Severity;
import com.bharathi.vulntrack.nvd.NvdResponse.CvssMetric;
import com.bharathi.vulntrack.nvd.NvdResponse.Description;
import com.bharathi.vulntrack.nvd.NvdResponse.Metrics;
import com.bharathi.vulntrack.nvd.NvdResponse.NvdCve;

/** Calls the NVD CVE API 2.0 for one CVE. No retries or caching here: that is added around it. */
public class NvdClient {

    private final RestClient restClient;

    public NvdClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * @return the CVE's details, or empty if NVD has no such CVE
     * @throws org.springframework.web.client.RestClientException on HTTP errors and timeouts
     */
    public Optional<CveDetails> fetch(String cveId) {
        NvdResponse response = restClient.get()
                .uri(uri -> uri.queryParam("cveId", cveId).build())
                .retrieve()
                .body(NvdResponse.class);

        if (response == null || response.vulnerabilities() == null || response.vulnerabilities().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toDetails(response.vulnerabilities().get(0).cve()));
    }

    private static CveDetails toDetails(NvdCve cve) {
        Optional<CvssMetric> metric = primaryMetric(cve.metrics());
        BigDecimal score = metric.map(m -> m.cvssData().baseScore()).orElse(null);
        String label = metric.map(m -> m.cvssData().baseSeverity()).orElse(null);
        return new CveDetails(
                cve.id(),
                Severity.from(label, score),
                score,
                englishDescription(cve.descriptions()),
                parseNvdTimestamp(cve.published()));
    }

    /** Prefer CVSS v3.1, then v3.0; within a version prefer NVD's own "Primary" score. */
    private static Optional<CvssMetric> primaryMetric(Metrics metrics) {
        if (metrics == null) {
            return Optional.empty();
        }
        return firstUsable(metrics.cvssMetricV31()).or(() -> firstUsable(metrics.cvssMetricV30()));
    }

    private static Optional<CvssMetric> firstUsable(List<CvssMetric> list) {
        if (list == null) {
            return Optional.empty();
        }
        List<CvssMetric> withData = list.stream().filter(m -> m.cvssData() != null).toList();
        return withData.stream()
                .filter(m -> "Primary".equals(m.type()))
                .findFirst()
                .or(() -> withData.stream().findFirst());
    }

    private static String englishDescription(List<Description> descriptions) {
        if (descriptions == null) {
            return null;
        }
        return descriptions.stream()
                .filter(d -> "en".equals(d.lang()))
                .map(Description::value)
                .findFirst()
                .orElse(null);
    }

    /** NVD timestamps have no zone (e.g. 2021-12-10T10:15:09.143) but are UTC. */
    private static Instant parseNvdTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
    }
}