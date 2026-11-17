package com.bharathi.vulntrack.domain;

import java.math.BigDecimal;
import java.util.Locale;

public enum Severity {
    CRITICAL, HIGH, MEDIUM, LOW;

    private static final BigDecimal CRITICAL_MIN = new BigDecimal("9.0");
    private static final BigDecimal HIGH_MIN = new BigDecimal("7.0");
    private static final BigDecimal MEDIUM_MIN = new BigDecimal("4.0");

    /** From a source's label (CRITICAL, MODERATE...) or, failing that, the CVSS v3 score bands. */
    public static Severity from(String label, BigDecimal score) {
        if (label != null) {
            switch (label.trim().toUpperCase(Locale.ROOT)) {
                case "CRITICAL" -> { return CRITICAL; }
                case "HIGH" -> { return HIGH; }
                case "MEDIUM", "MODERATE" -> { return MEDIUM; }
                case "LOW" -> { return LOW; }
                default -> { /* fall back to the score */ }
            }
        }
        if (score == null) {
            return MEDIUM;
        }
        if (score.compareTo(CRITICAL_MIN) >= 0) return CRITICAL;
        if (score.compareTo(HIGH_MIN) >= 0) return HIGH;
        if (score.compareTo(MEDIUM_MIN) >= 0) return MEDIUM;
        return LOW;
    }
}