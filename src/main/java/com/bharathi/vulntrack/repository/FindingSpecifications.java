package com.bharathi.vulntrack.repository;

import java.time.Instant;

import org.springframework.data.jpa.domain.Specification;

import com.bharathi.vulntrack.domain.Finding;
import com.bharathi.vulntrack.domain.FindingStatus;
import com.bharathi.vulntrack.domain.Severity;

public final class FindingSpecifications {

    private FindingSpecifications() {
    }

    public static Specification<Finding> hasStatus(FindingStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Finding> hasSeverity(Severity severity) {
        return (root, query, cb) -> cb.equal(root.get("cve").get("severity"), severity);
    }

    public static Specification<Finding> belongsToApplication(Long applicationId) {
        return (root, query, cb) ->
                cb.equal(root.get("dependency").get("application").get("id"), applicationId);
    }

    public static Specification<Finding> isOverdue(Instant now) {
        return (root, query, cb) -> cb.and(
                root.get("status").in(FindingStatus.OPEN, FindingStatus.IN_PROGRESS),
                cb.lessThan(root.<Instant>get("slaDueAt"), now));
    }
}