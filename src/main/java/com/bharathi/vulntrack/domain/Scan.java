package com.bharathi.vulntrack.domain;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "scan")
public class Scan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private ScanSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ScanStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_report", nullable = false)
    private String rawReport;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected Scan() {
        // required by JPA
    }

    public Scan(Application application, ScanSource source, String rawReport) {
        this.application = application;
        this.source = source;
        this.rawReport = rawReport;
        this.status = ScanStatus.RECEIVED;
    }

    @PrePersist
    @SuppressWarnings("unused") // called by Hibernate via reflection
    void onCreate() {
        if (submittedAt == null) {
            submittedAt = Instant.now();
        }
    }

    public void markProcessing() {
        this.status = ScanStatus.PROCESSING;
    }

    public void markCompleted(Instant at) {
        this.status = ScanStatus.COMPLETED;
        this.processedAt = at;
    }

    public void markFailed(Instant at) {
        this.status = ScanStatus.FAILED;
        this.processedAt = at;
    }

    public Long getId() { return id; }
    public Application getApplication() { return application; }
    public ScanSource getSource() { return source; }
    public ScanStatus getStatus() { return status; }
    public String getRawReport() { return rawReport; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getProcessedAt() { return processedAt; }
}