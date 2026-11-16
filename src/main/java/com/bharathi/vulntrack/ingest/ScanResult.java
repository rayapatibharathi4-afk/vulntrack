package com.bharathi.vulntrack.ingest;

/** What a scan changed. */
public record ScanResult(
        Long scanId,
        String application,
        int findingsInReport,
        int newFindings,
        int alreadyTracked,
        int fixed
) {
}