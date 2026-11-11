package com.bharathi.vulntrack.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bharathi.vulntrack.domain.Finding;
import com.bharathi.vulntrack.repository.FindingRepository;
import static com.bharathi.vulntrack.repository.FindingSpecifications.belongsToApplication;
import static com.bharathi.vulntrack.repository.FindingSpecifications.hasSeverity;
import static com.bharathi.vulntrack.repository.FindingSpecifications.hasStatus;
import static com.bharathi.vulntrack.repository.FindingSpecifications.isOverdue;
import com.bharathi.vulntrack.web.dto.FindingResponse;

@Service
public class FindingService {

    private final FindingRepository findings;
    private final Clock clock;

    public FindingService(FindingRepository findings, Clock clock) {
        this.findings = findings;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<FindingResponse> search(FindingFilter filter, Pageable pageable) {
        Instant now = clock.instant();

        List<Specification<Finding>> conditions = new ArrayList<>();
        if (filter.status() != null) {
            conditions.add(hasStatus(filter.status()));
        }
        if (filter.severity() != null) {
            conditions.add(hasSeverity(filter.severity()));
        }
        if (filter.applicationId() != null) {
            conditions.add(belongsToApplication(filter.applicationId()));
        }
        if (filter.overdue()) {
            conditions.add(isOverdue(now));
        }

        return findings.findAll(Specification.allOf(conditions), pageable)
                .map(finding -> FindingResponse.from(finding, now));
    }
}