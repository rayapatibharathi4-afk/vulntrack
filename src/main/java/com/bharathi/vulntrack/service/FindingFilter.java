package com.bharathi.vulntrack.service;

import com.bharathi.vulntrack.domain.FindingStatus;
import com.bharathi.vulntrack.domain.Severity;

public record FindingFilter(
        FindingStatus status,
        Severity severity,
        Long applicationId,
        boolean overdue
) {
}