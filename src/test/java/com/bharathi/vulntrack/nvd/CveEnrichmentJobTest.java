package com.bharathi.vulntrack.nvd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bharathi.vulntrack.domain.Cve;
import com.bharathi.vulntrack.domain.Severity;
import com.bharathi.vulntrack.repository.CveRepository;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CveEnrichmentJobTest {

    private final CveRepository cves = mock(CveRepository.class);
    private final ResilientNvdClient nvd = mock(ResilientNvdClient.class);
    private final CveEnricher enricher = mock(CveEnricher.class);
    private final NvdProperties props = new NvdProperties(
            "https://nvd.test", "", Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ZERO);

    private final CveEnrichmentJob job = new CveEnrichmentJob(cves, nvd, enricher, props);

    private static Cve cve(String id) {
        return new Cve(id, Severity.HIGH, null, null);
    }

    @Test
    void enrichesEveryPendingCve() {
        when(cves.findTop20ByFetchedAtIsNullOrderByIdAsc()).thenReturn(List.of(cve("CVE-A"), cve("CVE-B")));
        when(nvd.fetch(anyString())).thenReturn(Optional.empty());

        assertThat(job.enrichPending()).isEqualTo(2);
        verify(enricher).apply(eq("CVE-A"), any());
        verify(enricher).apply(eq("CVE-B"), any());
    }

    @Test
    void stopsTheRoundWhenNvdIsUnavailable() {
        when(cves.findTop20ByFetchedAtIsNullOrderByIdAsc())
                .thenReturn(List.of(cve("CVE-A"), cve("CVE-B"), cve("CVE-C")));
        when(nvd.fetch("CVE-A")).thenThrow(new NvdUnavailableException("NVD down", null));

        assertThat(job.enrichPending()).isZero();
        verify(nvd, times(1)).fetch(anyString());   // did not keep calling a service that is down
        verifyNoInteractions(enricher);              // nothing written
    }
}