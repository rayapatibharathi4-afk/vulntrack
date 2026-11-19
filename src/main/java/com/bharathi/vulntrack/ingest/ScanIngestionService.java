package com.bharathi.vulntrack.ingest;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bharathi.vulntrack.domain.Application;
import com.bharathi.vulntrack.domain.Cve;
import com.bharathi.vulntrack.domain.Dependency;
import com.bharathi.vulntrack.domain.Finding;
import com.bharathi.vulntrack.domain.FindingStatus;
import com.bharathi.vulntrack.domain.Scan;
import com.bharathi.vulntrack.domain.ScanSource;
import com.bharathi.vulntrack.domain.ScanStatus;
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
    private final ApplicationEventPublisher events;

    public ScanIngestionService(ApplicationRepository applications, ScanRepository scans,
                                DependencyRepository dependencies, CveRepository cves,
                                FindingRepository findings, DependencyCheckReportParser parser,
                                SlaCalculator slaCalculator, Clock clock,
                                ApplicationEventPublisher events) {
        this.applications = applications;
        this.scans = scans;
        this.dependencies = dependencies;
        this.cves = cves;
        this.findings = findings;
        this.parser = parser;
        this.slaCalculator = slaCalculator;
        this.clock = clock;
        this.events = events;
    }

    /** Async path, used by the API: validate, store as RECEIVED, hand off to Kafka after commit. */
    @Transactional
    public ScanResponse submit(String applicationName, String rawReport) {
        Application app = findApplication(applicationName);
        parser.parse(rawReport); // reject a broken report now (400), before anything is stored
        Scan scan = scans.save(new Scan(app, ScanSource.OWASP, rawReport, clock.instant()));
        events.publishEvent(new ScanSubmittedEvent(scan.getId(), app.getId()));
        return ScanResponse.from(scan);
    }

    /** Called by the Kafka consumer. Safe to call more than once for the same scan. */
    @Transactional
    public Optional<ScanResult> process(Long scanId) {
        Optional<Scan> found = scans.findById(scanId);
        if (found.isEmpty()) {
            log.warn("Scan {} not found, skipping", scanId);
            return Optional.empty();
        }
        Scan scan = found.get();
        if (scan.getStatus() == ScanStatus.COMPLETED || scan.getStatus() == ScanStatus.FAILED) {
            log.info("Scan {} is already {}, skipping duplicate delivery", scanId, scan.getStatus());
            return Optional.empty();
        }
        return Optional.of(processScan(scan, parser.parse(scan.getRawReport())));
    }

    /** Sync path, used by tests and tools: store and process in one transaction. */
    @Transactional
    public ScanResult ingest(String applicationName, String rawReport) {
        Application app = findApplication(applicationName);
        List<ParsedFinding> parsed = parser.parse(rawReport);
        Scan scan = scans.save(new Scan(app, ScanSource.OWASP, rawReport, clock.instant()));
        return processScan(scan, parsed);
    }

    @Transactional(readOnly = true)
    public ScanResponse getScan(Long id) {
        return scans.findById(id)
                .map(ScanResponse::from)
                .orElseThrow(() -> new NotFoundException("Scan " + id + " not found"));
    }

    private Application findApplication(String applicationName) {
        return applications.findByName(applicationName)
                .orElseThrow(() -> new NotFoundException(
                        "Application '" + applicationName + "' is not registered"));
    }

    private ScanResult processScan(Scan scan, List<ParsedFinding> parsed) {
        Application app = scan.getApplication();
        Instant now = clock.instant();
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