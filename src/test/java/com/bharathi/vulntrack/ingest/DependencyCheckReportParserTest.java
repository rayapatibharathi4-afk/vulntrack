package com.bharathi.vulntrack.ingest;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bharathi.vulntrack.domain.Ecosystem;
import com.bharathi.vulntrack.domain.Severity;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class DependencyCheckReportParserTest {

    private final DependencyCheckReportParser parser =
            new DependencyCheckReportParser(JsonMapper.builder().build());

    private static String sampleReport() throws IOException {
        try (InputStream in = DependencyCheckReportParserTest.class
                .getResourceAsStream("/reports/sample-dependency-check.json")) {
            return new String(in.readAllBytes(), UTF_8);
        }
    }

    @Test
    void extractsOneFindingPerPackageAndCve() throws IOException {
        List<ParsedFinding> findings = parser.parse(sampleReport());

        assertThat(findings)
                .extracting(ParsedFinding::cveId)
                .containsExactly("CVE-2021-44228", "CVE-2021-45046", "CVE-2022-22965", "CVE-2020-36518");
    }

    @Test
    void mapsPackageVersionSeverityAndScore() throws IOException {
        ParsedFinding log4shell = parser.parse(sampleReport()).get(0);

        assertThat(log4shell.ecosystem()).isEqualTo(Ecosystem.MAVEN);
        assertThat(log4shell.packageName()).isEqualTo("org.apache.logging.log4j:log4j-core");
        assertThat(log4shell.version()).isEqualTo("2.14.1");
        assertThat(log4shell.severity()).isEqualTo(Severity.CRITICAL);
        assertThat(log4shell.cvssScore()).isEqualByComparingTo("10.0");
    }

    @Test
    void rejectsInvalidJson() {
        assertThatThrownBy(() -> parser.parse("{ not json"))
                .isInstanceOf(InvalidReportException.class);
    }

    @Test
    void rejectsReportWithoutDependencies() {
        assertThatThrownBy(() -> parser.parse("{\"projectInfo\": {\"name\": \"x\"}}"))
                .isInstanceOf(InvalidReportException.class)
                .hasMessageContaining("dependencies");
    }

    @Test
    void parsesMavenAndScopedNpmPackageUrls() {
        assertThat(PackageCoordinates.fromPurl("pkg:maven/org.springframework/spring-beans@5.3.17"))
                .contains(new PackageCoordinates(Ecosystem.MAVEN, "org.springframework:spring-beans", "5.3.17"));
        assertThat(PackageCoordinates.fromPurl("pkg:npm/%40angular/core@12.0.0"))
                .contains(new PackageCoordinates(Ecosystem.NPM, "@angular/core", "12.0.0"));
        assertThat(PackageCoordinates.fromPurl("pkg:pypi/django@4.0")).isEmpty();
        assertThat(PackageCoordinates.fromPurl("not-a-purl")).isEmpty();
    }

    @Test
    void mapsModerateAndMissingSeverities() {
        assertThat(DependencyCheckReportParser.toSeverity("MODERATE", null)).isEqualTo(Severity.MEDIUM);
        assertThat(DependencyCheckReportParser.toSeverity(null, new BigDecimal("9.8"))).isEqualTo(Severity.CRITICAL);
        assertThat(DependencyCheckReportParser.toSeverity(null, new BigDecimal("6.5"))).isEqualTo(Severity.MEDIUM);
        assertThat(DependencyCheckReportParser.toSeverity(null, null)).isEqualTo(Severity.MEDIUM);
    }
}