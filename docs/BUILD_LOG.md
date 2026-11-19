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
