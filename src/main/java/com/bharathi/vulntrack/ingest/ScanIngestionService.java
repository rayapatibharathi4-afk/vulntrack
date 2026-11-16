package com.bharathi.vulntrack.ingest;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bharathi.vulntrack.domain.Application;
import com.bharathi.vulntrack.domain.Cve;
import com.bharathi.vulntrack.domain.Dependency;
import com.bharathi.vulntrack.domain.Finding;
import com.bharathi.vulntrack.domain.FindingStatus;
import com.bharathi.vulntrack.domain.Scan;
import com.bharathi.vulntrack.domain.ScanSource;
import com.bharathi.vulntrack.repository.ApplicationRepository;
import com.bharathi.vulntrack.repository.CveRepository;
import com.bharathi.vulntrack.repository.DependencyRepository;
import com.bharathi.vulntrack.repository.FindingRepository;
import com.bharathi.vulntrack.repository.ScanRepository;
import com.bharathi.vulntrack.service.NotFoundException;
import com.bharathi.vulntrack.service.SlaCalculator;
import com.bharathi.vulntrack.web.dto.ScanResponse;

@Service
public class ScanIngestionService {

    private static final Logger log = LoggerFactory.getLogger(ScanIngestionService.class);
    private static final List<FindingStatus> ACTIVE = List.of(FindingStatus.OPEN, FindingStatus.IN_PROGRESS);

    private final ApplicationRepository applications;
    private final ScanRepository scans;
    private final DependencyRepository dependencies;
    private final CveRepository cves;
    private final FindingRepository findings;
    private final DependencyCheckReportParser parser;
    private final SlaCalculator slaCalculator;
    private final Clock clock;

    public ScanIngestionService(ApplicationRepository applications, ScanRepository scans,
                                DependencyRepository dependencies, CveRepository cves,
                                FindingRepository findings, DependencyCheckReportParser parser,
                                SlaCalculator slaCalculator, Clock clock) {
        this.applications = applications;
        this.scans = scans;
        this.dependencies = dependencies;
        this.cves = cves;
        this.findings = findings;
        this.parser = parser;
        this.slaCalculator = slaCalculator;
        this.clock = clock;
    }

    @Transactional
    public ScanResult ingest(String applicationName, String rawReport) {
        Application app = applications.findByName(applicationName)
                .orElseThrow(() -> new NotFoundException(
                        "Application '" + applicationName + "' is not registered"));

        List<ParsedFinding> parsed = parser.parse(rawReport);
        Instant now = clock.instant();

                        Scan scan = scans.save(new Scan(app, ScanSource.OWASP, rawReport, now));
        scan.markProcessing();

        Set<Long> seenFindingIds = new HashSet<>();
        int created = 0;

        for (ParsedFinding pf : parsed) {
            Dependency dependency = upsertDependency(app, pf);
            Cve cve = cves.findById(pf.cveId())
                    .orElseGet(() -> cves.save(
                            new Cve(pf.cveId(), pf.severity(), pf.cvssScore(), pf.description())));

            Optional<Finding> existing = findings.findByDependencyIdAndCveId(dependency.getId(), cve.getId());
            Finding finding;
            if (existing.isPresent()) {
                finding = existing.get();
                finding.markSeen(now);
            } else {
                finding = findings.save(new Finding(
                        dependency, cve, now, slaCalculator.dueDate(cve.getSeverity(), now)));
                created++;
            }
            seenFindingIds.add(finding.getId());
        }

        // Anything still active for this app but missing from this scan has been fixed
        int fixed = 0;
        for (Finding active : findings.findByDependencyApplicationIdAndStatusIn(app.getId(), ACTIVE)) {
            if (!seenFindingIds.contains(active.getId())) {
                active.markFixed(now);
                fixed++;
            }
        }

        scan.markCompleted(clock.instant());
        ScanResult result = new ScanResult(scan.getId(), app.getName(),
                parsed.size(), created, parsed.size() - created, fixed);
        log.info("Scan {} for {}: {} in report, {} new, {} fixed",
                scan.getId(), app.getName(), parsed.size(), created, fixed);
        return result;
    }

    @Transactional(readOnly = true)
    public ScanResponse getScan(Long id) {
        return scans.findById(id)
                .map(ScanResponse::from)
                .orElseThrow(() -> new NotFoundException("Scan " + id + " not found"));
    }

    private Dependency upsertDependency(Application app, ParsedFinding pf) {
        return dependencies
                .findByApplicationIdAndEcosystemAndName(app.getId(), pf.ecosystem(), pf.packageName())
                .map(dep -> {
                    if (!dep.getVersion().equals(pf.version())) {
                        dep.updateVersion(pf.version());
                    }
                    return dep;
                })
                .orElseGet(() -> dependencies.save(
                        new Dependency(app, pf.ecosystem(), pf.packageName(), pf.version())));
    }
}
