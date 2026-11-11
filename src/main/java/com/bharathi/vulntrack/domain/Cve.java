package com.bharathi.vulntrack.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "cve")
public class Cve {

    @Id
    @Column(name = "id", length = 30)
    private String id;

    @Column(name = "cvss_score", precision = 3, scale = 1)
    private BigDecimal cvssScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 10)
    private Severity severity;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "fetched_at")
    private Instant fetchedAt;

    protected Cve() {
        // required by JPA
    }

    public Cve(String id, Severity severity, BigDecimal cvssScore, String description) {
        this.id = id;
        this.severity = severity;
        this.cvssScore = cvssScore;
        this.description = description;
    }

    public String getId() { return id; }
    public BigDecimal getCvssScore() { return cvssScore; }
    public Severity getSeverity() { return severity; }
    public String getDescription() { return description; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getFetchedAt() { return fetchedAt; }
}