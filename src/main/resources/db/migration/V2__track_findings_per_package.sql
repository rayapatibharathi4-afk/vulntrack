-- Findings are tracked per package + CVE, not per exact version.
-- A version bump that is still vulnerable keeps the same finding and SLA clock.

-- 1. A dependency is now "this package in this application"
ALTER TABLE dependency DROP CONSTRAINT uq_dependency;
ALTER TABLE dependency ADD CONSTRAINT uq_dependency UNIQUE (application_id, ecosystem, name);
COMMENT ON COLUMN dependency.version IS 'Version seen in the latest scan; updated in place';

-- 2. Remember which version a finding was first detected in
ALTER TABLE finding ADD COLUMN first_seen_version VARCHAR(100);

UPDATE finding f
SET first_seen_version = d.version
FROM dependency d
WHERE d.id = f.dependency_id;

ALTER TABLE finding ALTER COLUMN first_seen_version SET NOT NULL;