package com.bharathi.vulntrack.domain;

import java.time.Instant;

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
import jakarta.persistence.Table;

@Entity
@Table(name = "finding")
public class Finding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dependency_id", nullable = false)
    private Dependency dependency;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cve_id", nullable = false)
    private Cve cve;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FindingStatus status;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "sla_due_at", nullable = false)
    private Instant slaDueAt;

    @Column(name = "fixed_at")
    private Instant fixedAt;

    @Column(name = "assignee", length = 100)
    private String assignee;

    protected Finding() {
        // required by JPA
    }

    public Finding(Dependency dependency, Cve cve, Instant seenAt, Instant slaDueAt) {
        this.dependency = dependency;
        this.cve = cve;
        this.status = FindingStatus.OPEN;
        this.firstSeenAt = seenAt;
        this.lastSeenAt = seenAt;
        this.slaDueAt = slaDueAt;
    }

    /** The scanner reported this problem again. */
    public void markSeen(Instant seenAt) {
        this.lastSeenAt = seenAt;
        if (status == FindingStatus.FIXED) {
            // it came back: a regression
            this.status = FindingStatus.OPEN;
            this.fixedAt = null;
        }
    }

    /** The latest scan no longer contains this problem. */
    public void markFixed(Instant fixedAt) {
        this.status = FindingStatus.FIXED;
        this.fixedAt = fixedAt;
    }

    public void assignTo(String assignee) {
        this.assignee = assignee;
        if (status == FindingStatus.OPEN) {
            this.status = FindingStatus.IN_PROGRESS;
        }
    }

    public void acceptRisk() {
        this.status = FindingStatus.ACCEPTED_RISK;
    }

    public boolean isOverdue(Instant now) {
        boolean active = status == FindingStatus.OPEN || status == FindingStatus.IN_PROGRESS;
        return active && slaDueAt.isBefore(now);
    }

    public Long getId() { return id; }
    public Dependency getDependency() { return dependency; }
    public Cve getCve() { return cve; }
    public FindingStatus getStatus() { return status; }
    public Instant getFirstSeenAt() { return firstSeenAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public Instant getSlaDueAt() { return slaDueAt; }
    public Instant getFixedAt() { return fixedAt; }
    public String getAssignee() { return assignee; }
}