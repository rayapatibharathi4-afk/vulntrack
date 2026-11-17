package com.bharathi.vulntrack.ingest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.bharathi.vulntrack.domain.Severity;
import com.bharathi.vulntrack.ingest.DependencyCheckReport.ReportDependency;
import com.bharathi.vulntrack.ingest.DependencyCheckReport.ReportPackage;
import com.bharathi.vulntrack.ingest.DependencyCheckReport.ReportVulnerability;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class DependencyCheckReportParser {

    private static final Logger log = LoggerFactory.getLogger(DependencyCheckReportParser.class);

    private final JsonMapper jsonMapper;

    public DependencyCheckReportParser(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public List<ParsedFinding> parse(String rawJson) {
        DependencyCheckReport report;
        try {
            report = jsonMapper.readValue(rawJson, DependencyCheckReport.class);
        } catch (JacksonException e) {
            throw new InvalidReportException("Report is not valid Dependency-Check JSON", e);
        }
        if (report == null || report.dependencies() == null) {
            throw new InvalidReportException("Report has no 'dependencies' array");
        }

        // One entry per package + CVE, even if the report lists it more than once
        Map<String, ParsedFinding> unique = new LinkedHashMap<>();

        for (ReportDependency dep : report.dependencies()) {
            if (dep.vulnerabilities() == null || dep.vulnerabilities().isEmpty()) {
                continue; // a clean library
            }
            Optional<PackageCoordinates> coords = coordinatesOf(dep);
            if (coords.isEmpty()) {
                log.warn("Skipping {}: no Maven or npm package URL", dep.fileName());
                continue;
            }
            PackageCoordinates pkg = coords.get();
            for (ReportVulnerability vuln : dep.vulnerabilities()) {
                if (vuln.name() == null || !vuln.name().startsWith("CVE-")) {
                    continue; // only CVE identifiers are tracked
                }
                BigDecimal score = vuln.cvssv3() == null ? null : vuln.cvssv3().baseScore();
                String key = pkg.ecosystem() + "|" + pkg.name() + "|" + vuln.name();
                unique.putIfAbsent(key, new ParsedFinding(
                        pkg.ecosystem(), pkg.name(), pkg.version(), vuln.name(),
                        Severity.from(vuln.severity(), score), score, vuln.description()));
            }
        }
        return new ArrayList<>(unique.values());
    }

    private static Optional<PackageCoordinates> coordinatesOf(ReportDependency dep) {
        if (dep.packages() == null) {
            return Optional.empty();
        }
        for (ReportPackage p : dep.packages()) {
            Optional<PackageCoordinates> coords = PackageCoordinates.fromPurl(p.id());
            if (coords.isPresent()) {
                return coords;
            }
        }
        return Optional.empty();
    }

    
}