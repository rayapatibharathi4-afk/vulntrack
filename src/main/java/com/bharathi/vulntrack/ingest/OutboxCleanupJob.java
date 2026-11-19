package com.bharathi.vulntrack.ingest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.bharathi.vulntrack.repository.OutboxEventRepository;

/**
 * Deletes outbox rows that were sent long ago. Without this the table grows forever. Only sent rows are
 * deleted: an unsent row is still waiting for the relay, however old it is.
 */
@Component
@ConditionalOnProperty(name = "vulntrack.outbox-cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(OutboxCleanupJob.class);

    private final OutboxEventRepository outbox;
    private final Clock clock;
    private final Duration retention;

    public OutboxCleanupJob(OutboxEventRepository outbox, Clock clock,
                            @Value("${vulntrack.outbox.retention:7d}") Duration retention) {
        this.outbox = outbox;
        this.clock = clock;
        this.retention = retention;
    }

    /**
     * Returns how many sent rows were deleted. @Transactional is on the scheduled method, for the same reason as
     * the relay: calls from inside the class would skip the proxy.
     */
    @Scheduled(initialDelayString = "${vulntrack.outbox-cleanup.initial-delay:10m}",
               fixedDelayString = "${vulntrack.outbox-cleanup.delay:1h}")
    @Transactional
    public int cleanUp() {
        Instant cutoff = clock.instant().minus(retention);
        int deleted = outbox.deleteSentBefore(cutoff);
        if (deleted > 0) {
            log.info("Deleted {} sent outbox event(s) older than {}", deleted, retention);
        }
        return deleted;
    }
}
