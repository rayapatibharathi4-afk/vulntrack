package com.bharathi.vulntrack.web.dto;

import java.time.Instant;

import com.bharathi.vulntrack.domain.Scan;
import com.bharathi.vulntrack.domain.ScanSource;
import com.bharathi.vulntrack.domain.ScanStatus;

public record ScanResponse(
        Long id,
        String application,
        ScanSource source,
        ScanStatus status,
        Instant submittedAt,
        Instant processedAt
) {
    public static ScanResponse from(Scan scan) {
        return new ScanResponse(
                scan.getId(),
                scan.getApplication().getName(),
                scan.getSource(),
                scan.getStatus(),
                scan.getSubmittedAt(),
                scan.getProcessedAt());
    }
}