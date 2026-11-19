# VulnTrack build log

## Milestone 4 — Step 2: async scan submission through Kafka

**What changed**
- `ScanIngestionService` split into `submit` (validate, store as `RECEIVED`, publish event),
  `process` (consumer path, idempotent) and `ingest` (sync path, kept for tests/tools).
- `ScanSubmittedEvent` (record: `scanId`, `applicationId`) — the in-process event.
- `ScanEventPublisher` — `@TransactionalEventListener(AFTER_COMMIT)`, sends to `scan-submitted`
  with key = applicationId, value = scanId. Runs only after the scan row is committed.
- `ScanConsumer` — `@KafkaListener` on `scan-submitted`, calls `process(scanId)`.
  Can be switched off with `vulntrack.scan-consumer.enabled=false`.
- `ScanController` POST returns **202 Accepted** with `Location: /api/scans/{id}`.

**Errors hit**
1. File-name mismatch reported as `ScanSubmittedevent.java` vs `ScanSubmittedEvent`.
   Root cause: a typo in the earlier edit. On disk the file and class are both `ScanSubmittedEvent`,
   so nothing to fix now. Keep names exact (CI runs on Linux, which is case-sensitive).
2. Live POST returned `400 Report is not valid Dependency-Check JSON`.
   Root cause: my test tooling. PowerShell 5.1 `Out-File -Encoding utf8` writes a UTF-8 BOM
   (bytes `EF BB BF`) and the JSON parser rejects it. Sending the original file bytes gave 202.
   Open question for later: should the parser tolerate a BOM? Windows tools often add one.
3. `Invoke-WebRequest -SkipHttpErrorCheck` does not exist in Windows PowerShell 5.1. Used `curl.exe -i`.

**Verification**
- `.\mvnw.cmd clean verify`: exit 0, 32 tests in 9 classes (same count as before Step 2).
- Live (app on :8080, Postgres + Kafka from compose):
  - `POST /api/scans?application=live-demo-app` → `202`, `Location: /api/scans/81`, status `RECEIVED`.
  - Log: `Published scan 81 to scan-submitted partition 1 offset 0` (after commit), then
    `Received scan 81`, then `4 in report, 4 new, 0 fixed`.
  - `GET /api/scans/81` → `COMPLETED`.
  - `kafka-consumer-groups --describe vulntrack-scan-processor`: partition 1 current 1, log end 1, lag 0.

**Alternatives considered**
- Publish inside the transaction: rejected. The consumer could read a row that is not committed yet,
  and a rollback would leave a message for a scan that does not exist.
- Publish after commit with an event listener (chosen): no phantom messages. Still a dual-write gap:
  if the app crashes between commit and send, the scan stays `RECEIVED`. See the outbox note in the plan.

**Known gaps (for later steps)**
- Duplicate-delivery test not written yet — Step 4.

## Milestone 6 — Step 4: first CI run of the dependency scan failed (missing NVD key)

**Symptom:** GitHub Actions run for `7238bfb` (the cleanup commit): `build` passed, `dependency-check` failed at the OWASP step
after 16 seconds. A full NVD download takes far longer, so this was not a vulnerability finding.

**Root cause:** the scanner (dependency-check 13.0.0) refuses to start without an NVD API key:
`Invalid API Key, length of 0 too short`. The `NVD_API_KEY` repository secret is not set. Reproduced locally with the
same command and an empty key.

**Fix:** a guard step fails the job first, with the message to add the secret. The secret itself has to be added in
GitHub (Settings → Secrets and variables → Actions); it is not in the repository.

**Not an issue:** a PKIX warning from an unrelated Maven repository (`jvnet-nexus-releases`) in the same output.

## Milestone 5 — Step 8: metrics need the read role

**What changed:** `/actuator/prometheus` and `/actuator/metrics/**` require `ROLE_VIEWER`. The risk key (which has only
`ROLE_RISK`) now gets `403`. Before this, any valid key could read metrics. Test added: `theRiskKeyCannotReadMetrics`.
**Verification:** `clean verify` exit 0, 69 tests green.

## Review pass (before the final live checks)

**Checked with commands:** `Instant.now()` and `currentTimeMillis` in main code; file names against class names; tracked
files for secret-like values; `.gitignore` for local env files.

**Found and fixed**
- `Application` and `Scan` fell back to `Instant.now()` in `@PrePersist`. This broke the rule that time comes from the injected
  `Clock`. In `Application` the fallback ran on every create, because the constructor never set `createdAt`. Fix: `Application`
  takes `createdAt` from `ApplicationService`, which now has a `Clock`. The `@PrePersist` fallbacks are removed in both entities.
- `.env` was not in `.gitignore`, although compose reads keys from `.env`. Added `.env`, `*.env` and `.env.*`.

**Found, not fixed (decision needed)**
- The `outbox_event` table is never cleaned up. Sent rows stay forever. A small scheduled job that deletes sent rows older than a
  retention period would fix it.
- `/actuator/prometheus` accepts any valid key, including the risk key. The risk key should only be able to accept risks.
- Unknown URLs return 401 for anonymous callers, not 404 (noted earlier).

**Verification:** `clean verify` exit 0, 67 tests green.

## Milestone 5 — Step 7: expiry job for accepted risks

**What changed**
- `Finding.reopenIfRiskExpired(now)`: the one place that reopens an expired acceptance (clears its details and sets `OPEN`).
  `markSeen` (the scan path) now calls it, so both paths share the rule.
- `FindingRepository.findByStatusAndRiskExpiresAtBefore(status, cutoff)`: the job's query.
- `RiskExpiryJob` (service): hourly `@Scheduled` job (`vulntrack.risk-expiry.delay`, first run after `initial-delay`), reads expired
  acceptances, and reopens them. Logs how many it reopened. Switch-off property: `vulntrack.risk-expiry.enabled`.
- Every test context disables the job or delays its first run by an hour, so no background work runs during tests.

**Decisions**
- Hourly, not per-finding timers: one query per hour, simple to reason about, and a risk review deadline is measured in days.
- Reopened findings are already past their SLA date, so they appear as overdue straight away.

**Verification**
- `.\mvnw.cmd clean verify`: exit 0, 67 tests green (+2 domain tests for reopening, +1 integration test: expired acceptances reopen,
  active ones stay accepted; this test commits its own rows against a Postgres container).
- Live check: **not run.** Old app still on port 8080.

## Extension of Milestone 4 — transactional outbox (closes the dual-write gap)

**What changed**
- `V4__outbox.sql`: table `outbox_event` (topic, message key, payload, correlation id, created and sent times), plus a
  partial index on unsent rows.
- `OutboxEvent` (entity) and `OutboxEventRepository`. The repository's query locks rows with `FOR NO KEY UPDATE SKIP LOCKED`
  (Hibernate timeout hint -2), so two instances take different rows.
- `ScanIngestionService.submit` writes the scan and the outbox row in one transaction. The correlation id is copied into the row.
- `OutboxRelay`: `@Scheduled` every second (`vulntrack.outbox.delay`). Reads up to 100 unsent rows oldest first, sends each one
  and waits up to 10 seconds for the acknowledgement, marks it sent, and stops at the first failure.
- Removed `ScanEventPublisher` and `ScanSubmittedEvent` (the after-commit event). The outbox replaces them.
- `vulntrack.outbox.enabled=false` in every test context except the new end-to-end test, so background sends do not run in
  other tests.

**Errors hit**
1. The first Kafka run of the end-to-end test failed with `No active transaction`. Root cause: `relay()` (scheduled) called
   `relayPending()` (annotated `@Transactional`) on the same object. Self-invocation bypasses the proxy, so no transaction
   started, and the locking query needs one. Fix: `@Transactional` now sits on the scheduled method itself.
2. My first import edit to `ScanIngestionService` dropped the Micrometer imports. The compile error named `MeterRegistry`.
   Restored. Lesson: check the import block after a multi-line edit.
3. The IDE showed "cannot be resolved" errors for new classes until the build ran. The compiler was the source of truth.

**Alternatives considered**
- Debezium / change data capture: more infrastructure than this project needs. The relay is about 50 lines.
- Sending the message inside the transaction: rejected. A Kafka send cannot be rolled back, so a failed commit would leave a
  message for a scan that does not exist.
- Parallel sends: faster, but breaks the order per application. Kept sequential.

**Verification**
- `.\mvnw.cmd clean verify`: exit 0, 64 tests green (+3 relay unit tests, +1 service test for the outbox row, +1 end-to-end test
  with a real broker: submit, relay sends, consumer completes the scan, outbox row marked sent).
- The SQL log shows `for no key update of oe1_0 skip locked`.
- Live check on the dev stack: **not run.** Old app still on port 8080.

## Milestone 5 — Step 6: risk acceptance role

**What changed**
- `V3__risk_acceptance.sql`: adds `risk_reason`, `risk_accepted_by`, `risk_accepted_at`, `risk_expires_at`, and a check
  constraint: an `ACCEPTED_RISK` finding must have all four. Existing rows: none accepted, so the constraint applies cleanly
  (checked on the dev database before committing).
- `Finding`: `acceptRisk(reason, acceptedBy, acceptedAt, expiresAt)` replaces the empty `acceptRisk()`. It allows only open or
  in-progress findings, a non-blank reason and acceptor, and an expiry after the acceptance time. Violations throw
  `IllegalStateException` or `IllegalArgumentException`; the service maps them to 409 and 400.
- `markSeen`: an accepted finding whose expiry has passed, and which a scan still reports, goes back to `OPEN`, and its
  acceptance details are cleared. Before expiry it stays accepted.
- `POST /api/findings/{id}/accept-risk`: body `reason`, `acceptedBy`, `expiresAt`. Returns the finding.
- Third key `VULNTRACK_RISK_API_KEY` grants `ROLE_RISK` only. The route rule for accept-risk is listed before the general
  `POST /api/**` rule, because the first matching rule wins.
- Compose and `application.properties` pass the new key through.

**Decisions**
- Separate key, not an admin role: the creator of scans and the accepter of risks should not be the same credential.
- The expiry is enforced at the next scan, not by a timer. A scheduled job would be the next step.
- `acceptedBy` is recorded as given. Without user accounts it cannot be verified; the README says so.

**Verification**
- `.\mvnw.cmd clean verify`: exit 0, 61 tests green (+11: six domain tests on the rules and expiry, five web tests on the keys
  and validation).
- Dev database: no `ACCEPTED_RISK` rows, so the new constraint applies without errors.
- Live check: **not run.** Old app still on port 8080.

## Milestone 6 — Step 2: OWASP Dependency-Check in CI

**What changed**
- `pom.xml`: profile `security-scan` with `dependency-check-maven` 13.0.0 (published 2026-08-03, older than two weeks)
  and `failBuildOnCVSS=7`, so any CVE with CVSS 7.0 or higher fails the build. The plugin is in a profile only: it is
  not bound to any lifecycle phase, so `clean verify` never runs it.
- `.github/workflows/ci.yml`: new job `dependency-check`. It runs `./mvnw -B -Psecurity-scan org.owasp:dependency-check-maven:check`
  on pushes to `main` and weekly (cron). It is skipped on pull requests because the NVD download is slow.
  The NVD key comes from the repository secret `NVD_API_KEY` through `env:`. The reports are uploaded as an artifact.
  Timeout 90 minutes.

**Why a separate job and profile**
- The first run downloads the whole NVD database. Without a key the API allows far fewer requests, so the download
  can take hours. Putting it in every build would make pull requests unusable.

**Verification**
- `./mvnw -Psecurity-scan help:effective-pom`: the profile's settings (`failBuildOnCVSS=7`, version 13.0.0) are active.
  The default effective POM does not include the plugin in its build.
- `clean verify` still passes: exit 0, 50 tests green.
- **Not run:** the scan itself. It needs the full NVD download, which I did not start locally (hours without a key).
  The first CI run is the real test. Also needed: add the `NVD_API_KEY` repository secret. Without it, the first run
  may hit the 90-minute timeout.

**Alternatives considered**
- Running the scan on every pull request: safer, but too slow and too many NVD requests.
- Caching the NVD database between runs (`actions/cache`): would make later runs faster. Not added yet; it is an easy follow-up.
- A suppression file for known false positives: not needed yet. If the first run flags something that is not a real
  risk, add it to a suppression file with a reason.

## Milestone 6 — Step 1: container image and the app in compose

**What changed**
- `Dockerfile`: two stages. A JDK image runs `mvnw package -DskipTests` (tests run in CI). A JRE image gets only the
  jar, a fixed system user (`vulntrack`, uid 999) and no login shell. Dependencies are fetched in a separate layer so
  code changes rebuild quickly. No secret is in the image.
- `.dockerignore`: keeps `.git`, `docs`, `target` and `.env` out of the build context.
- `docker-compose.yml`:
  - Kafka gets an `INTERNAL` listener (`kafka:29092`) for containers on the compose network. The `PLAINTEXT` listener
    (`localhost:9092`) is unchanged for the host.
  - New `app` service on `8080`, with its database and Kafka addresses set to the compose service names.
  - Keys come from the environment. `VULNTRACK_API_KEY` is optional at compose level: an empty key refuses writes.
    A required variable would also block `docker compose up kafka` for anyone who only needs the broker.

**Errors hit**
- `${VULNTRACK_API_KEY:?...}` made every compose command fail when the key was unset, even `up kafka`. Changed to
  `${VULNTRACK_API_KEY:-}`.

**Verification**
- `docker build -t vulntrack:dev .` succeeded. The image runs as uid 999.
- Recreated only the Kafka container with the new listeners (healthy).
- Started the image on the compose network on port 8081, with the consumer disabled so it does not compete with the
  process on 8080. Health: `UP` (reached Postgres). `POST /api/scans` without a key: `401`. With the key: `202`, and the
  log shows `Published scan 199 ... partition 1 offset 0` over the internal listener.
- Container removed afterwards.
- Not done: the container's consumer and full scan processing, and the live duplicate and poison demo (the old
  Step 2 process on 8080 is still running, which is why the checks were run on 8081 with the consumer off).

## Milestone 5 — Step 5: read access and roles

**What changed**
- Two keys. `VULNTRACK_API_KEY` grants ROLE_INGEST and ROLE_VIEWER. `VULNTRACK_READ_API_KEY` grants ROLE_VIEWER only.
- Rules: POST /api/** needs INGEST. GET /api/** needs VIEWER. `/actuator/health/**`, `/actuator/info` and `/error` are open.
  Everything else (including `/actuator/prometheus`) needs any valid key.
- Missing or unknown key: 401. Valid key without the role: 403. Both are Problem Details (`accessDeniedHandler` added).
- The filter compares hashes of both keys in constant time. An unset key matches nothing.

**Behaviour change**
- An unknown URL without a key now returns 401, not 404. The `anyRequest().authenticated()` rule applies before
  the 404 handler. With a valid key the 404 still happens. Accepted for now; a 404 for anonymous callers would
  need a rule for unmapped paths.

**Decision**
- No separate admin role yet. The plan's "role for risk acceptance" needs an endpoint that does not exist, so
  it's left for when that endpoint is built. Two roles (INGEST, VIEWER) are enough for CI and dashboards.

**Verification**
- `.\mvnw.cmd clean verify`: exit 0, 50 tests green (+4: read with read key, no key on read, read-only key forbidden
  on write, ingest key can read).
- Live check: **not run.** Old app still on port 8080.

## Milestone 5 — Step 4: Testcontainers for PostgreSQL and Kafka

**What changed**
- `PostgresContainerConfig` (PostgreSQL 16) and `KafkaContainerConfig` (Apache Kafka 4.1.0): test-only
  `@ServiceConnection` beans. Boot points the datasource and Kafka at the containers. Tests import only what they need.
- Tests that touched the database (`VulntrackApplicationTests`, `ScanIngestionServiceTest`, `ScanEventPublisherTest`)
  import the Postgres config. `ScanDeadLetterKafkaTest` and `MetricsEndpointTest` import both.
- `@EmbeddedKafka` is gone. The dead-letter test reads from the container with a plain `KafkaConsumer`.
- `.github/workflows/ci.yml`: the Postgres `services:` block is removed. Ubuntu runners include Docker.
- Dependencies (test scope, versions from Boot's Testcontainers 2.0.5 BOM): `spring-boot-testcontainers`,
  `testcontainers-postgresql`, `testcontainers-kafka`.

**Errors hit**
- The dead-letter test timed out. The test read `spring.kafka.bootstrap-servers` with `@Value`, which gave the local
  compose broker (`localhost:9092`). The producer and listeners were on the container. Two cluster IDs in the log
  showed it. Root cause: `@ServiceConnection` supplies a `KafkaConnectionDetails` bean. It does not change the raw
  property or the `KafkaProperties` bean. Fix: inject `KafkaConnectionDetails`.

**Verification**
- `.\mvnw.cmd clean verify`: exit 0, 46 tests green.
- The test logs show every JDBC URL on a random container port (not 5432), and one Kafka cluster ID for the whole run.
- Live check: **not run.** The old app is still on port 8080.

## Milestone 5 — Step 3: metrics for Kafka lag, the NVD circuit breaker and scans processed

**What changed**
- `/actuator/prometheus` is exposed (with health, info and metrics). Dependencies: `micrometer-registry-prometheus`
  (version from Boot) and `resilience4j-micrometer` 2.3.0 (same version as the other resilience4j modules).
- NVD circuit breaker: now created through a `CircuitBreakerRegistry` and bound with `TaggedCircuitBreakerMetrics`,
  so `resilience4j_circuitbreaker_state` and the call counters appear. The test helper `NvdConfig.circuitBreaker(...)` is
  unchanged, so the existing tests still use it.
- `vulntrack.scans.processed{outcome=completed|failed}`: counted in `processScan` and in `markFailed`.
- Kafka consumer lag: Boot registers Spring Kafka's consumer metrics with Micrometer. Nothing was added by hand.
  `kafka_consumer_fetch_manager_records_lag` is checked in the test.

**Errors hit**
- `TaggedCircuitBreakerMetrics.ofCircuitBreaker` does not exist in 2.3.0. The class only has registry-based factories.
  Checked the jar with `javap`, then built the breaker through a registry.
- `/actuator/prometheus` returned 404 in the test. Boot's test support turns metrics export off unless the test has
  `@AutoConfigureMetrics`. The condition report (`-Ddebug=true`) showed it: "management.defaults.metrics.export.enabled
  is considered false".
- Boot 4 moved test annotations: `AutoConfigureMockMvc` is in `org.springframework.boot.webmvc.test.autoconfigure`,
  `AutoConfigureMetrics` is in `org.springframework.boot.micrometer.metrics.test.autoconfigure`.

**Known gap**
- `/actuator/prometheus` is public: the security rules allow all GETs. Before this is exposed outside a private network,
  restrict the actuator endpoints (planned with the read roles).

**Verification**
- `.\mvnw.cmd clean verify`: exit 0, 46 tests green (`MetricsEndpointTest` added, with embedded Kafka).
- Live check: **not run.** Old app still on port 8080.

## Milestone 5 — Step 2: correlation IDs across HTTP, logs and Kafka

**What changed**
- `CorrelationIdFilter` (first in the chain): takes `X-Correlation-Id` from the request or makes a UUID. Puts it in the
  logging context (MDC), echoes it in the response, and clears it at the end (threads are reused).
- Incoming ids are accepted only if they match `[A-Za-z0-9-]{1,64}`. Anything else is replaced. This stops a client
  from injecting newlines into the logs.
- `ScanEventPublisher` copies the id into a Kafka header on the message. The send callback runs on Kafka's thread,
  so the id is read on the request thread first and passed in.
- `ScanConsumer` restores the id from the header while processing, then clears it.
- Log pattern `%5p [%X{correlationId:-}]` prints the id on every line.

**Errors hit**
- `ScanEventPublisherTest` failed after the change. It still verified the old `send(topic, key, value)` overload, and the
  publisher now sends a `ProducerRecord`. Fixed by capturing the record and checking topic, key, value and header.

**Known gap**
- If the DLT recoverer logs a failure, the consumer's MDC has already been cleared, so that log line has no id. The
  id is still on the dead-letter record as a header.

**Verification**
- `.\mvnw.cmd clean verify`: exit 0, 45 tests green. Filter tests (4): echo, unsafe id replaced, id generated,
  context cleared. Publisher test now checks the header. DLT test checks that the header survives the dead-letter hop.
- Live check: **not run** (old app still on port 8080).

## Milestone 5 — Step 1: API key on write endpoints

**What changed**
- `SecurityConfig`: `POST /api/**` needs the `ROLE_INGEST` role. Reads stay open. CSRF, HTTP Basic and form login are
  off (stateless API, no cookies). Failures return `401` as RFC 9457 Problem Details.
- `ApiKeyAuthFilter`: reads the `X-API-Key` header and grants `ROLE_INGEST` when it matches. It compares SHA-256
  hashes with `MessageDigest.isEqual`, so the comparison time does not depend on the key's contents.
- The key comes from `VULNTRACK_API_KEY` (`vulntrack.security.api-key=${VULNTRACK_API_KEY:}`). If it is empty,
  nothing matches, so writes fail closed.
- The filter is not a `@Component`, so Boot does not also register it as a servlet filter.

**Errors hit**
- None in the build. The Spring Security starters were not in the local cache, so the first build downloaded them.

**Verification**
- `.\mvnw.cmd clean verify`: exit 0, 41 tests green (39 before, +2 for the missing-key and wrong-key paths).
- Live check: **not run.** The old Step 2 app is still on port 8080 and doesn't have security. Pending the restart.

**Alternatives considered**
- JWT: more machinery (signing keys, expiry, rotation). The plan allows either; an API key is enough for CI
  callers, and it is the simpler thing to defend in an interview. Roles can be added to the key later.
- Keys in the database (hashed, per application): better for rotation, but it needs a migration and an admin
  flow. Left for later.
- Testing with a dummy key in `@TestPropertySource` is not a secret. It only exists in the test source.

## Milestone 4 — Step 4: tests for idempotency, publish-after-commit, dead-letter path

**Bug found by the new test (fixed in this step, introduced in Step 3)**
- Dead-lettered records went to `scan-submitted-dlt`, not `scan-submitted.DLT`. Spring's
  `DeadLetterPublishingRecoverer` default name is `<topic>-dlt`. Unit tests did not catch it because nothing
  checked the topic name. Fix: pass an explicit destination resolver to `KafkaTopics.SCAN_SUBMITTED_DLT`.
  With broker auto-create on, the wrong topic would have been created silently.

**What was added**
- `ScanEventPublisherTest` (3): nothing is sent until commit; nothing is sent after rollback; a failed send
  is logged, not thrown. Uses a `TransactionTemplate` and a mocked `KafkaTemplate`, so it writes no rows.
- `ScanIngestionServiceTest` (+3): a duplicate `process` does the work once; `markFailed` flags an
  unfinished scan; `markFailed` does not overwrite a COMPLETED scan.
- `ScanDeadLetterKafkaTest` (1): end to end on an in-process broker (`@EmbeddedKafka`, no Docker). A poison
  message is dead-lettered, and the root-cause header is `NumberFormatException`.
- `pom.xml`: added `spring-kafka-test` (test scope, version managed by Boot).

**Errors hit while getting the embedded-Kafka test green**
1. Timeout, no record on the DLT. Root cause: the producer sent before any listener had a partition. Fixed by
   waiting for an assignment (`KafkaListenerEndpointRegistry`) before sending.
2. Topics were created with 1 partition by `@EmbeddedKafka`, while the `KafkaTopics` beans create 3. The
   broker then created partitions while consumers joined, with NOT_COORDINATOR errors. Fixed by using 3 partitions.
3. The DLT reader failed on the key. `KafkaTestUtils.consumerProps` defaults keys to Integer. Set String explicitly.
4. The exception header holds Spring's wrapper `ListenerExecutionFailedException`. Asserted the cause header
   `kafka_dlt-exception-cause-fqcn` instead.
- `KafkaTestUtils.consumerProps(String,String,EmbeddedKafkaBroker)` is deprecated in Spring Kafka 4. It still
  works. Replace before upgrading.

**Verification**
- `.\mvnw.cmd clean verify`: exit 0, 39 tests green (was 32).
- Live demo of duplicate and poison messages: **pending.** The Step 2 app is still on port 8080 and the
  Step 3 code (including the topic fix) is not loaded there.

## Milestone 4 — Step 3: retries, dead-letter topic, FAILED status

**What changed**
- `ScanErrorHandlingConfig`: a `DefaultErrorHandler` bean. Boot picks it up for the listener container.
  - Retries: 3 retries with exponential backoff, starting at 1s and doubling (1s, 2s, 4s), then give up.
  - Not retried: `NumberFormatException` (message is not a scan id) and `InvalidReportException` (bad report).
  - Recoverer: marks the scan FAILED, then publishes the record to `scan-submitted.DLT`
    (`DeadLetterPublishingRecoverer`, same partition number).
- `ScanIngestionService.markFailed(scanId)`: idempotent. Leaves COMPLETED and FAILED scans alone.
- `application.properties`: added `spring.kafka.listener.concurrency=3` and
  `spring.kafka.producer.properties.max.block.ms=5000`. Step 2 notes listed these, but they were missing from the file.

**Errors hit**
- `ExponentialBackOff` has no `setMaxRetries`. Used `ExponentialBackOffWithMaxRetries`, which lives in
  `org.springframework.kafka.support`, not `org.springframework.util.backoff`. Found it by listing the jar.

**Verification**
- `.\mvnw.cmd clean verify`: exit 0, 32 tests green.
- Live DLT path: **not yet run.** The Step 2 app process is still on port 8080 and could not be stopped
  from here, so the new handler is not loaded. Pending a restart.

**Alternatives considered**
- Mark FAILED inside the consumer's catch block: rejected. It would also run on retryable errors, and the
  scan's transaction has already rolled back, so it needs its own transaction. The recoverer is the one place
  that knows retries are exhausted.
- A separate listener on `scan-submitted.DLT` that marks scans FAILED: also works, but it is one more consumer
  to run and to keep idempotent. Chose the recoverer so the mark and the publish happen in one call.
