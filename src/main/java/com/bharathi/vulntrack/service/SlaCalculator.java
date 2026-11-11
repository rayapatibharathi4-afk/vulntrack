package com.bharathi.vulntrack.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.bharathi.vulntrack.domain.Severity;

@Component
public class SlaCalculator {

    public Instant dueDate(Severity severity, Instant firstSeenAt) {
        Objects.requireNonNull(severity, "severity must not be null");
        Objects.requireNonNull(firstSeenAt, "firstSeenAt must not be null");
        return firstSeenAt.plus(slaDays(severity), ChronoUnit.DAYS);
    }

    public int slaDays(Severity severity) {
        return switch (severity) {
            case CRITICAL -> 7;
            case HIGH -> 30;
            case MEDIUM -> 90;
            case LOW -> 180;
        };
    }
}