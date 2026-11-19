-- Risk acceptance: who accepted a finding, why, and until when.
-- A new migration; V1 and V2 are already applied and must not change.

ALTER TABLE finding
    ADD COLUMN risk_reason      VARCHAR(500),
    ADD COLUMN risk_accepted_by VARCHAR(100),
    ADD COLUMN risk_accepted_at TIMESTAMPTZ,
    ADD COLUMN risk_expires_at  TIMESTAMPTZ;

-- The database refuses an accepted finding that is missing its details
ALTER TABLE finding
    ADD CONSTRAINT chk_finding_risk_acceptance CHECK (
        status <> 'ACCEPTED_RISK'
        OR (risk_reason IS NOT NULL
            AND risk_accepted_by IS NOT NULL
            AND risk_accepted_at IS NOT NULL
            AND risk_expires_at IS NOT NULL)
    );
