package com.bharathi.vulntrack.nvd;

import com.bharathi.vulntrack.domain.Cve;
import com.bharathi.vulntrack.repository.CveRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "vulntrack.enrichment.enabled", havingValue = "true", matchIfMissing = true)
public class CveEnrichmentJob {

    private static final Logger log = LoggerFactory.getLogger(CveEnrichmentJob.class);

    private final CveRepository cves;
    private final ResilientNvdClient nvd;
    private final CveEnricher enricher;
    private final NvdProperties props;

    public CveEnrichmentJob(CveRepository cves, ResilientNvdClient nvd,
                            CveEnricher enricher, NvdProperties props) {
        this.cves = cves;
        this.nvd = nvd;
        this.enricher = enricher;
        this.props = props;
    }

    @Scheduled(initialDelayString = "${vulntrack.enrichment.initial-delay}",
               fixedDelayString = "${vulntrack.enrichment.delay}")
    public void run() {
        enrichPending();
    }

    /** Enrich one batch. Returns how many CVEs were processed. */
    int enrichPending() {
        List<Cve> pending = cves.findTop20ByFetchedAtIsNullOrderByIdAsc();
        int processed = 0;
        for (Cve cve : pending) {
            try {
                enricher.apply(cve.getId(), nvd.fetch(cve.getId()));
                processed++;
            } catch (NvdUnavailableException e) {
                log.warn("NVD unavailable, will retry next round: {}", e.getMessage());
                break;
            }
            if (!pause()) {
                break;
            }
        }
        if (processed > 0) {
            log.info("Enriched {} of {} pending CVEs from NVD", processed, pending.size());
        }
        return processed;
    }

    /** Respect NVD's rate limit between requests. */
    private boolean pause() {
        try {
            Thread.sleep(props.requestInterval());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}