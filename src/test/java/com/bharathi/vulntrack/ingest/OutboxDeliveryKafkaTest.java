package com.bharathi.vulntrack.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.bharathi.vulntrack.KafkaContainerConfig;
import com.bharathi.vulntrack.PostgresContainerConfig;
import com.bharathi.vulntrack.domain.Application;
import com.bharathi.vulntrack.domain.ScanStatus;
import com.bharathi.vulntrack.repository.ApplicationRepository;
import com.bharathi.vulntrack.repository.OutboxEventRepository;
import com.bharathi.vulntrack.repository.ScanRepository;
import com.bharathi.vulntrack.web.dto.ScanResponse;

/**
 * End to end, with a real broker: a submitted scan is written to the outbox, the relay sends it, the
 * consumer processes it, and the outbox row ends up marked as sent. No @Transactional here, because
 * the relay runs in its own thread and must see committed rows.
 */
@SpringBootTest(properties = {
        "vulntrack.enrichment.enabled=false",
        "vulntrack.scan-consumer.enabled=true",
        "vulntrack.outbox.enabled=true",
        "vulntrack.outbox.delay=500ms",
        "vulntrack.risk-expiry.enabled=false"
})
@Import({PostgresContainerConfig.class, KafkaContainerConfig.class})
class OutboxDeliveryKafkaTest {

    @Autowired
    ScanIngestionService service;

    @Autowired
    ApplicationRepository applications;

    @Autowired
    ScanRepository scans;

    @Autowired
    OutboxEventRepository outbox;

    @Test
    void submittedScanIsSentByTheRelayAndProcessed() throws IOException, InterruptedException {
        applications.save(new Application("outbox-app", null, "test"));
        ScanResponse accepted = service.submit("outbox-app", sampleReport());
        Long scanId = accepted.id();

        awaitTrue(() -> scans.findById(scanId)
                .map(scan -> scan.getStatus() == ScanStatus.COMPLETED)
                .orElse(false), "scan " + scanId + " to be processed");

        assertThat(outbox.findAll())
                .filteredOn(event -> event.getPayload().equals(String.valueOf(scanId)))
                .singleElement()
                .satisfies(event -> assertThat(event.getSentAt()).isNotNull());
    }

    private static String sampleReport() throws IOException {
        try (InputStream in = OutboxDeliveryKafkaTest.class.getResourceAsStream("/reports/sample-dependency-check.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Polls until the condition holds, or fails after 60 seconds. */
    private static void awaitTrue(BooleanSupplier condition, String what) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Timed out waiting for " + what);
            }
            Thread.sleep(200);
        }
    }
}
