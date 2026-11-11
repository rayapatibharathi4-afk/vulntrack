package com.bharathi.vulntrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bharathi.vulntrack.domain.Severity;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SlaCalculatorTest {

    private static final Instant FIRST_SEEN = Instant.parse("2026-11-01T10:00:00Z");

    private final SlaCalculator calculator = new SlaCalculator();

    @ParameterizedTest(name = "{0} is due on {1}")
    @CsvSource({
        "CRITICAL, 2026-11-08T10:00:00Z",
        "HIGH,     2026-12-01T10:00:00Z",
        "MEDIUM,   2027-01-30T10:00:00Z",
        "LOW,      2027-04-30T10:00:00Z"
    })
    void dueDateDependsOnSeverity(Severity severity, Instant expectedDue) {
        assertThat(calculator.dueDate(severity, FIRST_SEEN)).isEqualTo(expectedDue);
    }

    @Test
    void rejectsMissingSeverity() {
        assertThatThrownBy(() -> calculator.dueDate(null, FIRST_SEEN))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("severity");
    }
}