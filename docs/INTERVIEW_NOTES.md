# VulnTrack: interview notes

Simple points for each milestone. Each one: what it does, why it is built that way, the trade-off,
and one likely question with an answer. The detailed history (errors and their causes) is in `BUILD_LOG.md`.

---

## 30-second pitch

VulnTrack takes vulnerability scan reports from CI, turns each vulnerable library into a tracked finding with a
deadline based on severity, and marks it fixed when a later scan no longer reports it. Scans are processed
through Kafka, so CI never waits for NVD or for a large report. It is one Spring Boot app with a REST API and a
Kafka consumer, packaged as a container, with API-key auth, metrics and a CI pipeline that runs a dependency scan.

---

## M0: setup

- **What:** Spring Boot 4 app, PostgreSQL 16 in Docker Compose, a health endpoint.
- **Why Postgres in Docker and not H2:** the tests and the app use the same database engine. JSONB columns and
  unique constraints behave the same way in both.
- **Trade-off:** you need Docker running to start the stack.
- **Likely question:** *Why not an embedded database for tests?* Because behaviour differences between databases
  are a source of bugs. A real database in a container removes the difference.

## M1: data model and CRUD

- **What:** Flyway migrations, JPA entities, DTOs as records, paged APIs, RFC 9457 Problem Details for every error.
- **Why Flyway and `ddl-auto=validate`:** the schema changes only through versioned SQL that is reviewed. Hibernate
  checks that the entities match the schema, and the app refuses to start on a mismatch.
- **Why DTOs and not entities over HTTP:** the API contract does not change when the table does, and clients
  cannot set fields they should not.
- **Why no N+1:** relations are `LAZY`, and the findings query loads what it needs in one statement (`@EntityGraph`).
- **Why an injected `Clock`:** overdue logic is tested with fixed dates instead of depending on the real time.
- **Likely question:** *What is an N+1 query and how did you avoid it?* One query for the list, then one extra query
  per row for each lazy relation. An entity graph (or a join fetch) loads the relation in the first query.

## M2: scan ingestion

- **What:** `POST` a Dependency-Check report. The app creates or updates dependencies, CVEs and findings, and marks
  findings `FIXED` when a later scan no longer reports them.
- **Why the unique constraint on `(dependency, cve)`:** the same report can be processed twice. The constraint
  makes the second write harmless, even when two writers race.
- **Trade-off:** a finding that disappears is marked fixed by absence. If the scanner fails silently, every finding
  looks fixed. A future improvement is to check that the scanner actually ran.
- **Likely question:** *How do you stop duplicates when two scans run at the same time?* The database enforces
  uniqueness, so one insert wins and the other is an update.

## M3: NVD enrichment

- **What:** a client for the NVD API with timeouts, retries with exponential backoff and jitter, a circuit breaker,
  a 24-hour cache, and a scheduled job that enriches pending CVEs in batches with pauses between calls.
- **Why a circuit breaker:** when NVD is down, every call waits for its timeout. The breaker opens after repeated
  failures and fails fast, so the job does not pile up waiting threads.
- **Why jitter:** without it, many clients retry at the same moment and overload the service again.
- **Why the key comes from an environment variable:** a secret in the code or in the repo leaks with the code.
- **Likely question:** *What does the circuit breaker do in each state?* Closed: calls pass through and failures
  are counted. Open: calls fail immediately for a wait period. Half-open: a few trial calls decide whether to close again.

## M4: asynchronous processing with Kafka

- **What:** `POST` stores the scan as `RECEIVED` and returns `202` with a `Location`. The message is sent only after
  the database commit. A consumer processes the scan and sets `COMPLETED`. Failed messages are retried three times
  with backoff, then go to `scan-submitted.DLT` and the scan becomes `FAILED`.
- **Why publish after commit:** the consumer must never see a scan that does not exist, so the message waits for the
  commit (`@TransactionalEventListener(AFTER_COMMIT)`).
- **Why a partition key of the application id:** messages for one application stay in order.
- **Why bad input is not retried:** a message that is not a number, or a report that cannot be parsed, will never
  succeed. Retrying only delays it and blocks the partition behind it.
- **The dual write, and how it was closed:** at first the scan was saved and then the message was sent. A crash in
  between left the scan `RECEIVED` forever. Now the scan and an outbox row are written in one transaction, and a relay
  sends the outbox rows. See the outbox section below.
- **Bugs found while testing (good interview material):**
  - The dead-letter topic was named `scan-submitted-dlt` by Spring's default, not `scan-submitted.DLT`. Found only by
    an end-to-end test that checked where the message actually went.
  - The test read the bootstrap address from the wrong place. `@ServiceConnection` replaces the connection details
    bean, not the raw property.
  - A test sent a message before the listener owned a partition, so the message was never read.
- **Likely question:** *What happens if the same message arrives twice?* The consumer checks the status first. A
  scan that is already `COMPLETED` or `FAILED` is skipped, so the duplicate does no work.

## M5: security, observability, tests

- **API keys with roles:** one key can read and write (CI), another can only read (dashboards, Prometheus).
  Missing or wrong key: `401`. Valid key without permission: `403`. Both are Problem Details.
- **Why hash the keys before comparing:** comparing SHA-256 digests in constant time means the response time does not
  reveal how much of the key matched.
- **Why fail closed:** an unset key matches nothing, so forgetting the variable refuses writes rather than allowing them.
- **Correlation IDs:** each request gets an ID (or uses the client's, if it is safe). The ID is in every log line and
  travels in the Kafka message, so a failure in the consumer can be traced to the original POST. Client IDs are
  validated, so a client cannot inject newlines into the logs.
- **Metrics:** Kafka consumer lag, the NVD circuit breaker's state, and scans processed (completed and failed).
- **Testcontainers:** the tests start their own PostgreSQL and Kafka. CI needs no service containers, and a test cannot
  depend on a developer's local database.
- **Trade-off:** `/actuator/prometheus` needs any valid key, so protection depends on key handling.
- **Likely question:** *Why 403 and not 401 for a read-only key trying to write?* 401 means the caller is not
  identified. This key is identified and simply not allowed, so 403 is the accurate answer.

### Transactional outbox (extension of M4)

- **What:** the scan row and an `outbox_event` row are written in the same transaction. A scheduled relay reads
  the unsent rows, sends each one to Kafka, and marks it sent only after the broker acknowledges it.
- **Why:** the old design saved the scan and then sent the message. A crash between the two left the scan
  `RECEIVED` forever. Now the message exists if and only if the scan exists, because they commit together.
- **Why the relay stops at the first failure:** sending later rows past a failed one would break the order per
  application. The trade-off is that one stuck message delays the ones behind it.
- **Why a crash after send can duplicate a message:** the mark happens after the send. A crash in between sends it
  again. The consumer already skips finished scans, so the duplicate does no work.
- **Multiple instances:** the relay selects rows with `FOR NO KEY UPDATE SKIP LOCKED`, so two instances take different
  rows. The SQL in the logs shows the clause.
- **Bug found while building it:** the scheduled method called another method on the same class that was marked
  `@Transactional`. The call skipped the proxy, so there was no transaction and the lock query failed with
  "No active transaction". Fix: the annotation goes on the scheduled method itself.
- **Likely question:** *What is the difference between at-most-once, at-least-once and exactly-once?* At-most-once can lose
  a message; this design never does that. At-least-once can repeat one, which this design allows, and the consumer makes
  repeats harmless. Exactly-once across a database and Kafka needs transactions spanning both, which Kafka cannot
  provide with a database.

### Risk acceptance (closing out M5)

- **What:** a security owner, with a separate key, accepts a finding's risk with a reason and an expiry date.
  The status becomes `ACCEPTED_RISK`, and the acceptance is excluded from overdue. A scan that still reports the
  finding after the expiry reopens it.
- **Why a separate role:** separation of duties. The team that creates scans should not also be able to accept their
  risks. Here the ingest key cannot accept, and the risk key cannot create scans.
- **Why the database enforces the details:** a check constraint refuses an accepted finding with no reason, expiry, or
  acceptor, even if a bug skips the service.
- **Trade-off:** the expiry only takes effect at the next scan. Nothing runs on the clock. A scheduled job would close that.
- **Likely question:** *Why does acceptance need an expiry at all?* A risk accepted forever is never reviewed. An expiry
  forces a periodic decision.

## M6: shipping

- **Container image:** two stages. A JDK stage builds the jar; a JRE stage runs only the jar, as a non-root user.
  The build tools and source are not in the image.
- **Compose:** the full stack runs with one command. Kafka has two listeners: `localhost:9092` for the host and
  `kafka:29092` for containers on the compose network.
- **Dependency scan:** OWASP Dependency-Check runs as a separate CI job on `main` and weekly, and fails on CVSS 7.0
  or higher. It is not on every pull request because the NVD download is slow.
- **Trade-off:** a vulnerable change can merge before the weekly scan catches it. The scan shortens the window; it
  does not close it.
- **Likely question:** *Why is the dependency scan separate from the build?* A check that takes hours on every commit
  gets skipped. Running it on `main` and on a schedule keeps the policy and keeps pull requests fast.
- **Likely question:** *Why does the container run as a non-root user?* If the process is compromised, the attacker
  only has the permissions of an unprivileged user, not root in the container.

---

## Known gaps, stated plainly

- One stuck outbox message delays the messages behind it until it is fixed (by design, to keep order).
- An expired risk acceptance reopens only at the next scan, not on the clock.
- `acceptedBy` is written as given; it is not an authenticated identity.
- No assigning or closing of findings through the API.
- API keys are static: no rotation, no per-client identity, no audit log.
- Dead-letter messages are replayed by hand.
- No distributed tracing; correlation IDs are logs and headers only.
