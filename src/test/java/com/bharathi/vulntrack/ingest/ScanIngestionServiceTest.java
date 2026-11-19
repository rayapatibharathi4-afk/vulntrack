package com.bharathi.vulntrack.ingest;

import java.io.IOException;
import java.io.InputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.bharathi.vulntrack.PostgresContainerConfig;
import org.springframework.transaction.annotation.Transactional;

import com.bharathi.vulntrack.domain.Application;
import com.bharathi.vulntrack.domain.Finding;
import com.bharathi.vulntrack.domain.FindingStatus;
import com.bharathi.vulntrack.domain.ScanStatus;
import com.bharathi.vulntrack.repository.ApplicationRepository;
import com.bharathi.vulntrack.repository.FindingRepository;
import com.bharathi.vulntrack.repository.OutboxEventRepository;
import com.bharathi.vulntrack.service.NotFoundException;

@SpringBootTest(properties = {
        "vulntrack.enrichment.enabled=false",
        "vulntrack.scan-consumer.enabled=false",
        "vulntrack.outbox.enabled=false",
        "vulntrack.risk-expiry.enabled=false",
        "vulntrack.outbox-cleanup.enabled=false",
        "spring.kafka.admin.auto-create=false"
})
@Import(PostgresContainerConfig.class)
@Transactional
class ScanIngestionServiceTest {

    private static final String APP = "ingest-test-app";

    @Autowired
    private ScanIngestionService service;

    @Autowired
    private ApplicationRepository applications;

    @Autowired
    private FindingRepository findings;

    @Autowired
    private OutboxEventRepository outbox;

    private Application app;
    private String report;

    @BeforeEach
    void setUp() throws IOException {
        app = applications.save(new Application(APP, null, "test", Instant.parse("2026-11-01T10:00:00Z")));
        try (InputStream in = getClass().getResourceAsStream("/reports/sample-dependency-check.json")) {
            report = new String(in.readAllBytes(), UTF_8);
        }
    }

    private List<Finding> appFindings() {
        return findings.findAll().stream()
                .filter(f -> f.getDependency().getApplication().getId().equals(app.getId()))
                .toList();
    }

    private Finding findingFor(String cveId) {
        return appFindings().stream()
                .filter(f -> f.getCve().getId().equals(cveId))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void firstScanCreatesOneOpenFindingPerPackageAndCve() {
        ScanResult result = service.ingest(APP, report);

        assertThat(result.newFindings()).isEqualTo(4);
        assertThat(result.fixed()).isZero();
        assertThat(appFindings())
                .hasSize(4)
                .allMatch(f -> f.getStatus() == FindingStatus.OPEN);
    }

    @Test
    void sameReportTwiceCreatesNoDuplicates() {
        service.ingest(APP, report);
        ScanResult second = service.ingest(APP, report);

        assertThat(second.newFindings()).isZero();
        assertThat(second.alreadyTracked()).isEqualTo(4);
        assertThat(appFindings()).hasSize(4);
    }

    @Test
    void findingMissingFromNextScanIsMarkedFixed() {
        service.ingest(APP, report);

        // Simulate jackson-databind being upgraded: its CVE no longer appears
        String withoutJacksonCve = report.replace("\"CVE-2020-36518\"", "\"NOT-A-CVE\"");
        ScanResult second = service.ingest(APP, withoutJacksonCve);

        assertThat(second.fixed()).isEqualTo(1);
        Finding jackson = findingFor("CVE-2020-36518");
        assertThat(jackson.getStatus()).isEqualTo(FindingStatus.FIXED);
        assertThat(jackson.getFixedAt()).isNotNull();
    }

    @Test
    void stillVulnerableVersionBumpKeepsTheSameFindingAndDeadline() {
        service.ingest(APP, report);
        Finding before = findingFor("CVE-2021-44228");
        Long idBefore = before.getId();
        Instant deadlineBefore = before.getSlaDueAt();

        service.ingest(APP, report.replace("log4j-core@2.14.1", "log4j-core@2.15.0"));

        Finding after = findingFor("CVE-2021-44228");
        assertThat(after.getId()).isEqualTo(idBefore);
        assertThat(after.getSlaDueAt()).isEqualTo(deadlineBefore);
        assertThat(after.getFirstSeenVersion()).isEqualTo("2.14.1");
        assertThat(after.getDependency().getVersion()).isEqualTo("2.15.0");
        assertThat(after.getStatus()).isEqualTo(FindingStatus.OPEN);
    }

    @Test
    void submitWritesTheMessageToTheOutboxInTheSameTransaction() {
        Long scanId = service.submit(APP, report).id();

        assertThat(outbox.findAll())
                .filteredOn(event -> event.getPayload().equals(String.valueOf(scanId)))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getTopic()).isEqualTo(KafkaTopics.SCAN_SUBMITTED);
                    assertThat(event.getMessageKey()).isEqualTo(String.valueOf(app.getId()));
                    assertThat(event.getSentAt()).isNull(); // not sent yet: the relay does that later
                });
    }

    @Test
    void processingTheSameScanTwiceOnlyDoesTheWorkOnce() {
        Long scanId = service.submit(APP, report).id();

        assertThat(service.process(scanId)).isPresent();
        assertThat(service.process(scanId)).isEmpty(); // duplicate delivery from Kafka

        assertThat(appFindings()).hasSize(4);
        assertThat(service.getScan(scanId).status()).isEqualTo(ScanStatus.COMPLETED);
    }

    @Test
    void markFailedFlagsAnUnfinishedScan() {
        Long scanId = service.submit(APP, report).id();

        service.markFailed(scanId);

        assertThat(service.getScan(scanId).status()).isEqualTo(ScanStatus.FAILED);
        assertThat(service.getScan(scanId).processedAt()).isNotNull();
    }

    @Test
    void markFailedDoesNotOverwriteACompletedScan() {
        Long scanId = service.submit(APP, report).id();
        service.process(scanId);

        service.markFailed(scanId);

        assertThat(service.getScan(scanId).status()).isEqualTo(ScanStatus.COMPLETED);
    }

    @Test
    void unknownApplicationIsRejected() {
        assertThatThrownBy(() -> service.ingest("no-such-app", report))
                .isInstanceOf(NotFoundException.class);
    }
}