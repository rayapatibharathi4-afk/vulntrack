package com.bharathi.vulntrack.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FindingRiskAcceptanceTest {

    private static final Instant SEEN = Instant.parse("2026-11-01T10:00:00Z");
    private static final Instant ACCEPTED = Instant.parse("2026-11-10T10:00:00Z");
    private static final Instant EXPIRES = Instant.parse("2026-12-10T10:00:00Z");

    private Finding finding;

    @BeforeEach
    void setUp() {
        Application app = new Application("payments-service", null, "payments");
        Dependency dependency = new Dependency(app, Ecosystem.MAVEN, "log4j-core", "2.14.1");
        Cve cve = new Cve("CVE-2021-44228", Severity.CRITICAL, new BigDecimal("10.0"), "Log4Shell");
        finding = new Finding(dependency, cve, SEEN, SEEN.plusSeconds(7 * 86_400L));
    }

    @Test
    void acceptingAnOpenFindingRecordsWhoWhyAndUntil() {
        finding.acceptRisk("Only reachable from the internal network", "j.doe", ACCEPTED, EXPIRES);

        assertThat(finding.getStatus()).isEqualTo(FindingStatus.ACCEPTED_RISK);
        assertThat(finding.getRiskReason()).isEqualTo("Only reachable from the internal network");
        assertThat(finding.getRiskAcceptedBy()).isEqualTo("j.doe");
        assertThat(finding.getRiskAcceptedAt()).isEqualTo(ACCEPTED);
        assertThat(finding.getRiskExpiresAt()).isEqualTo(EXPIRES);
    }

    @Test
    void aFixedFindingCannotBeAccepted() {
        finding.markFixed(ACCEPTED);

        assertThatThrownBy(() -> finding.acceptRisk("reason", "j.doe", ACCEPTED, EXPIRES))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anAcceptanceMustExpireAfterItIsMade() {
        assertThatThrownBy(() -> finding.acceptRisk("reason", "j.doe", ACCEPTED, ACCEPTED.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aReasonIsRequired() {
        assertThatThrownBy(() -> finding.acceptRisk("  ", "j.doe", ACCEPTED, EXPIRES))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aScanBeforeExpiryKeepsTheAcceptance() {
        finding.acceptRisk("reason", "j.doe", ACCEPTED, EXPIRES);

        finding.markSeen(EXPIRES.minusSeconds(60));

        assertThat(finding.getStatus()).isEqualTo(FindingStatus.ACCEPTED_RISK);
    }

    @Test
    void aScanAfterExpiryReopensTheFindingAndClearsTheAcceptance() {
        finding.acceptRisk("reason", "j.doe", ACCEPTED, EXPIRES);

        finding.markSeen(EXPIRES.plusSeconds(60));

        assertThat(finding.getStatus()).isEqualTo(FindingStatus.OPEN);
        assertThat(finding.getRiskReason()).isNull();
        assertThat(finding.getRiskExpiresAt()).isNull();
    }
}
