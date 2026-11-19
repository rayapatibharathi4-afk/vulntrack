package com.bharathi.vulntrack.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.bharathi.vulntrack.PostgresContainerConfig;
import com.bharathi.vulntrack.domain.OutboxEvent;
import com.bharathi.vulntrack.repository.OutboxEventRepository;

/**
 * The cleanup against a real database. The job's first run is an hour away, so only this test calls it.
 * Rows are committed, so the payloads are unique to this test.
 */
@SpringBootTest(properties = {
        "vulntrack.enrichment.enabled=false",
        "vulntrack.scan-consumer.enabled=false",
        "vulntrack.outbox.enabled=false",
        "vulntrack.risk-expiry.enabled=false",
        "vulntrack.outbox-cleanup.initial-delay=1h",
        "vulntrack.outbox.retention=1d",
        "spring.kafka.admin.auto-create=false"
})
@Import(PostgresContainerConfig.class)
class OutboxCleanupJobTest {

    @Autowired
    OutboxCleanupJob job;

    @Autowired
    OutboxEventRepository outbox;

    @Autowired
    Clock clock;

    @Test
    void deletesOnlySentRowsOlderThanTheRetentionPeriod() {
        Instant now = clock.instant();

        OutboxEvent oldSent = new OutboxEvent(KafkaTopics.SCAN_SUBMITTED, "k", "cleanup-old-sent", null,
                now.minus(3, ChronoUnit.DAYS));
        oldSent.markSent(now.minus(3, ChronoUnit.DAYS));
        outbox.save(oldSent);

        OutboxEvent recentSent = new OutboxEvent(KafkaTopics.SCAN_SUBMITTED, "k", "cleanup-recent-sent", null,
                now.minus(1, ChronoUnit.HOURS));
        recentSent.markSent(now.minus(1, ChronoUnit.HOURS));
        outbox.save(recentSent);

        // Never sent, and much older than the retention period: still waiting, so it must stay
        outbox.save(new OutboxEvent(KafkaTopics.SCAN_SUBMITTED, "k", "cleanup-old-unsent", null,
                now.minus(30, ChronoUnit.DAYS)));

        int deleted = job.cleanUp();

        assertThat(deleted).isGreaterThanOrEqualTo(1);
        assertThat(outbox.findAll()).extracting(OutboxEvent::getPayload)
                .doesNotContain("cleanup-old-sent")
                .contains("cleanup-recent-sent", "cleanup-old-unsent");
    }
}
