package com.bharathi.vulntrack.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CveTest {

    private static final Instant NOW = Instant.parse("2026-11-17T12:00:00Z");

    @Test
    void enrichOverwritesWithNvdData() {
        Cve cve = new Cve("CVE-2021-44228", Severity.HIGH, new BigDecimal("8.0"), "from scanner");

        cve.enrich(Severity.CRITICAL, new BigDecimal("10.0"), "from NVD", NOW, NOW);

        assertThat(cve.getSeverity()).isEqualTo(Severity.CRITICAL);
        assertThat(cve.getCvssScore()).isEqualByComparingTo("10.0");
        assertThat(cve.getDescription()).isEqualTo("from NVD");
        assertThat(cve.getFetchedAt()).isEqualTo(NOW);
    }

    @Test
    void keepsScannerSeverityWhenNvdHasNoScoreYet() {
        Cve cve = new Cve("CVE-2026-0001", Severity.CRITICAL, new BigDecimal("9.8"), "from scanner");

        cve.enrich(Severity.MEDIUM, null, null, null, NOW);

        assertThat(cve.getSeverity()).isEqualTo(Severity.CRITICAL);
        assertThat(cve.getCvssScore()).isEqualByComparingTo("9.8");
        assertThat(cve.getDescription()).isEqualTo("from scanner");
        assertThat(cve.getFetchedAt()).isEqualTo(NOW);
    }

    @Test
    void markCheckedOnlyRecordsTheTime() {
        Cve cve = new Cve("CVE-2099-0001", Severity.LOW, null, "from scanner");

        cve.markChecked(NOW);

        assertThat(cve.getSeverity()).isEqualTo(Severity.LOW);
        assertThat(cve.getFetchedAt()).isEqualTo(NOW);
    }
}