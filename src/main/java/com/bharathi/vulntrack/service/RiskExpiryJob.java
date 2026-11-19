package com.bharathi.vulntrack.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.bharathi.vulntrack.domain.Finding;
import com.bharathi.vulntrack.domain.FindingStatus;
import com.bharathi.vulntrack.repository.FindingRepository;

/**
 * Reopens accepted risks whose expiry has passed, on the clock, without waiting for the next scan.
 * A reopened finding is past its SLA date already, so it shows as overdue straight away.
 */
@Component
@ConditionalOnProperty(name = "vulntrack.risk-expiry.enabled", havingValue = "true", matchIfMissing = true)
public class RiskExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(RiskExpiryJob.class);

    private final FindingRepository findings;
    private final Clock clock;

    public RiskExpiryJob(FindingRepository findings, Clock clock) {
        this.findings = findings;
        this.clock = clock;
    }

    /**
     * Returns how many findings were reopened. @Transactional is on this method because the scheduler calls it
     * through the proxy; a call from inside the same class would skip the transaction.
     */
    @Scheduled(initialDelayString = "${vulntrack.risk-expiry.initial-delay:1m}",
               fixedDelayString = "${vulntrack.risk-expiry.delay:1h}")
    @Transactional
    public int reopenExpired() {
        Instant now = clock.instant();
        List<Finding> expired = findings.findByStatusAndRiskExpiresAtBefore(FindingStatus.ACCEPTED_RISK, now);
        int reopened = 0;
        for (Finding finding : expired) {
            if (finding.reopenIfRiskExpired(now)) {
                reopened++;
            }
        }
        if (reopened > 0) {
            log.info("Reopened {} finding(s) whose accepted risk expired", reopened);
        }
        return reopened;
    }
}
