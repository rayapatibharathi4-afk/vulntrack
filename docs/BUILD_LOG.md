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
