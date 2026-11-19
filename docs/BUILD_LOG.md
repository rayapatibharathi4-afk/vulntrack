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
- No failure handling for poison messages yet — Step 3 (`DefaultErrorHandler` + DLT).
- Duplicate-delivery test not written yet — Step 4.
