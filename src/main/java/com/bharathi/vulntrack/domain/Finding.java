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
    
    @Column(name = "first_seen_version", nullable = false, length = 100)
    private String firstSeenVersion;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "sla_due_at", nullable = false)
    private Instant slaDueAt;

    @Column(name = "fixed_at")
    private Instant fixedAt;

    @Column(name = "assignee", length = 100)
    private String assignee;

    @Column(name = "risk_reason", length = 500)
    private String riskReason;

    @Column(name = "risk_accepted_by", length = 100)
    private String riskAcceptedBy;

    @Column(name = "risk_accepted_at")
    private Instant riskAcceptedAt;

    @Column(name = "risk_expires_at")
    private Instant riskExpiresAt;

    protected Finding() {
        // required by JPA
    }

    public Finding(Dependency dependency, Cve cve, Instant seenAt, Instant slaDueAt) {
        this.dependency = dependency;
        this.cve = cve;
        this.firstSeenVersion = dependency.getVersion();
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
        } else {
            // the acceptance ran out and the problem is still there: it needs attention again
            reopenIfRiskExpired(seenAt);
        }
    }

    /**
     * Turns an accepted risk back into an open finding once its expiry has passed. Returns true when it did.
     * Called by the scan path (the problem is still reported) and by the expiry job (on the clock).
     */
    public boolean reopenIfRiskExpired(Instant now) {
        if (status != FindingStatus.ACCEPTED_RISK || !isRiskAcceptanceExpired(now)) {
            return false;
        }
        this.status = FindingStatus.OPEN;
        clearRiskAcceptance();
        return true;
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

    /**
     * Records a decision to live with this problem until expiresAt. Only an open or in-progress
     * finding can be accepted. Throws IllegalStateException or IllegalArgumentException when the
     * rules are broken; the service turns those into HTTP responses.
     */
    public void acceptRisk(String reason, String acceptedBy, Instant acceptedAt, Instant expiresAt) {
        if (status != FindingStatus.OPEN && status != FindingStatus.IN_PROGRESS) {
            throw new IllegalStateException("Only an open finding can be accepted, this one is " + status);
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required to accept a risk");
        }
        if (acceptedBy == null || acceptedBy.isBlank()) {
            throw new IllegalArgumentException("Name who accepts the risk");
        }
        if (!expiresAt.isAfter(acceptedAt)) {
            throw new IllegalArgumentException("The acceptance must expire in the future");
        }
        this.status = FindingStatus.ACCEPTED_RISK;
        this.riskReason = reason;
        this.riskAcceptedBy = acceptedBy;
        this.riskAcceptedAt = acceptedAt;
        this.riskExpiresAt = expiresAt;
    }

    /** True once an accepted risk has run out. Only meaningful for ACCEPTED_RISK findings. */
    public boolean isRiskAcceptanceExpired(Instant now) {
        return riskExpiresAt != null && !now.isBefore(riskExpiresAt);
    }

    private void clearRiskAcceptance() {
        this.riskReason = null;
        this.riskAcceptedBy = null;
        this.riskAcceptedAt = null;
        this.riskExpiresAt = null;
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
    public String getFirstSeenVersion() { return firstSeenVersion; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public Instant getSlaDueAt() { return slaDueAt; }
    public Instant getFixedAt() { return fixedAt; }
    public String getAssignee() { return assignee; }
    public String getRiskReason() { return riskReason; }
    public String getRiskAcceptedBy() { return riskAcceptedBy; }
    public Instant getRiskAcceptedAt() { return riskAcceptedAt; }
    public Instant getRiskExpiresAt() { return riskExpiresAt; }
}