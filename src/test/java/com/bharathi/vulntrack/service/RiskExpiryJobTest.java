package com.bharathi.vulntrack.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.bharathi.vulntrack.PostgresContainerConfig;
import com.bharathi.vulntrack.domain.Application;
import com.bharathi.vulntrack.domain.Cve;
import com.bharathi.vulntrack.domain.Dependency;
import com.bharathi.vulntrack.domain.Ecosystem;
import com.bharathi.vulntrack.domain.Finding;
import com.bharathi.vulntrack.domain.FindingStatus;
import com.bharathi.vulntrack.domain.Severity;
import com.bharathi.vulntrack.repository.ApplicationRepository;
import com.bharathi.vulntrack.repository.CveRepository;
import com.bharathi.vulntrack.repository.DependencyRepository;
import com.bharathi.vulntrack.repository.FindingRepository;

/**
 * The expiry job against a real database. The job bean exists, but its first scheduled run is an hour away,
 * so only this test calls it. The test commits its own data, so names are unique and nothing is rolled back.
 */
@SpringBootTest(properties = {
        "vulntrack.enrichment.enabled=false",
        "vulntrack.scan-consumer.enabled=false",
        "vulntrack.outbox.enabled=false",
        "vulntrack.risk-expiry.initial-delay=1h",
        "spring.kafka.admin.auto-create=false"
})
@Import(PostgresContainerConfig.class)
class RiskExpiryJobTest {

    @Autowired
    RiskExpiryJob job;

    @Autowired
    Clock clock;

    @Autowired
    ApplicationRepository applications;

    @Autowired
    DependencyRepository dependencies;

    @Autowired
    CveRepository cves;

    @Autowired
    FindingRepository findings;

    @Test
    void reopensOnlyTheFindingsWhoseAcceptanceHasRunOut() {
        Instant now = clock.instant();
        Application app = applications.save(new Application("expiry-job-app", null, "test"));
        Dependency dependency = dependencies.save(new Dependency(app, Ecosystem.MAVEN, "expiry-lib", "1.0"));

        Finding expired = findings.save(acceptedFinding(dependency, "CVE-2099-EXPIRY-1", now, now.minus(10, ChronoUnit.DAYS),
                now.minus(1, ChronoUnit.DAYS)));
        Finding stillValid = findings.save(acceptedFinding(dependency, "CVE-2099-EXPIRY-2", now, now,
                now.plus(30, ChronoUnit.DAYS)));

        int reopened = job.reopenExpired();

        assertThat(reopened).isGreaterThanOrEqualTo(1);
        Finding reloadedExpired = findings.findById(expired.getId()).orElseThrow();
        assertThat(reloadedExpired.getStatus()).isEqualTo(FindingStatus.OPEN);
        assertThat(reloadedExpired.getRiskReason()).isNull();
        assertThat(findings.findById(stillValid.getId()).orElseThrow().getStatus())
                .isEqualTo(FindingStatus.ACCEPTED_RISK);
    }

    private Finding acceptedFinding(Dependency dependency, String cveId, Instant seenAt,
                                    Instant acceptedAt, Instant expiresAt) {
        Cve cve = cves.save(new Cve(cveId, Severity.HIGH, new BigDecimal("8.1"), "test finding"));
        Finding finding = new Finding(dependency, cve, seenAt, seenAt.plus(30, ChronoUnit.DAYS));
        finding.acceptRisk("reason for test", "tester", acceptedAt, expiresAt);
        return finding;
    }
}
