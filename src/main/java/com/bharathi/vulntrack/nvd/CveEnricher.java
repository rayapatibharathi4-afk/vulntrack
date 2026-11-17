package com.bharathi.vulntrack.nvd;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bharathi.vulntrack.repository.CveRepository;

@Service
public class CveEnricher {

    private final CveRepository cves;
    private final Clock clock;

    public CveEnricher(CveRepository cves, Clock clock) {
        this.cves = cves;
        this.clock = clock;
    }

    @Transactional
    public void apply(String cveId, Optional<CveDetails> details) {
        Instant now = clock.instant();
        cves.findById(cveId).ifPresent(cve -> details.ifPresentOrElse(
                d -> cve.enrich(d.severity(), d.cvssScore(), d.description(), d.publishedAt(), now),
                () -> cve.markChecked(now)));
    }
}