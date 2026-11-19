package com.bharathi.vulntrack.ingest;

/** A scan was stored and is ready to be processed. */
public record ScanSubmittedEvent(Long scanId, Long applicationId) {
}