-- Local development test data. Safe to run more than once.
-- Requires the applications 'payments-service' and 'auth-service' to exist.

INSERT INTO cve (id, cvss_score, severity, description) VALUES
  ('CVE-2021-44228', 10.0, 'CRITICAL', 'Log4Shell: remote code execution in Apache Log4j 2 via JNDI lookups'),
  ('CVE-2022-22965',  9.8, 'CRITICAL', 'Spring4Shell: remote code execution via Spring Framework data binding'),
  ('CVE-2020-36518',  7.5, 'HIGH',     'Denial of service in jackson-databind via deeply nested JSON'),
  ('CVE-2022-25857',  7.5, 'HIGH',     'Denial of service in SnakeYAML due to missing nesting depth limit')
ON CONFLICT (id) DO NOTHING;

INSERT INTO dependency (application_id, ecosystem, name, version)
SELECT a.id, 'MAVEN', v.name, v.version
FROM application a
JOIN (VALUES
  ('payments-service', 'org.apache.logging.log4j:log4j-core',          '2.14.1'),
  ('payments-service', 'org.springframework:spring-beans',             '5.3.17'),
  ('auth-service',     'com.fasterxml.jackson.core:jackson-databind',  '2.13.1'),
  ('auth-service',     'org.yaml:snakeyaml',                           '1.30')
) AS v(app, name, version) ON v.app = a.name
ON CONFLICT ON CONSTRAINT uq_dependency DO NOTHING;

INSERT INTO finding (dependency_id, cve_id, status, first_seen_at, last_seen_at, sla_due_at, fixed_at, assignee)
SELECT d.id, v.cve_id, v.status,
       now() - v.first_seen_ago, now() - interval '1 day',
       now() - v.first_seen_ago + v.sla, v.fixed_at, v.assignee
FROM dependency d
JOIN (VALUES
  -- CRITICAL, seen 10 days ago, 7-day SLA: overdue
  ('org.apache.logging.log4j:log4j-core', 'CVE-2021-44228', 'OPEN',
      interval '10 days', interval '7 days',  NULL::timestamptz, NULL),
  -- CRITICAL, seen 2 days ago, someone is on it: not overdue
  ('org.springframework:spring-beans', 'CVE-2022-22965', 'IN_PROGRESS',
      interval '2 days',  interval '7 days',  NULL::timestamptz, 'priya'),
  -- HIGH, seen 40 days ago, 30-day SLA: overdue
  ('com.fasterxml.jackson.core:jackson-databind', 'CVE-2020-36518', 'OPEN',
      interval '40 days', interval '30 days', NULL::timestamptz, NULL),
  -- HIGH, past its deadline but FIXED: must NOT count as overdue
  ('org.yaml:snakeyaml', 'CVE-2022-25857', 'FIXED',
      interval '60 days', interval '30 days', now() - interval '45 days', 'arjun')
) AS v(dep_name, cve_id, status, first_seen_ago, sla, fixed_at, assignee)
  ON v.dep_name = d.name
ON CONFLICT ON CONSTRAINT uq_finding DO NOTHING;