# Phase 3 Notes

Working notes from building Phase 3 (Workers) of `full_spec.md` — the Redis
queue, worker process, job execution (SHELL/HTTP), stdout/stderr capture, and
success/failure handling. Meant to save a new session from rediscovering the
decisions and quirks below. The code and git history are the source of
truth; this is context that isn't visible from reading the repo alone.

---

## Architecture decisions made

- **The worker is embedded in the `api` app behind `runway.worker.enabled`**,
  mirroring `SchedulerService`'s `@ConditionalOnProperty` pattern from Phase
  2, rather than standing up a separate process/module. The full_spec's
  diagrams (sections 9, 41) show scheduler/worker as separate boxes, but
  since the same jar can be deployed multiple times with different property
  flags to get that separation later, there's no engineering reason yet to
  pay for a second deployable — matches section 46's "prefer simple
  architecture first, only introduce additional infrastructure when there is
  an engineering reason."
- **Queue is a single Redis list** (`runway:executions:queue`), `LPUSH` /
  `BRPOP` via `ExecutionQueueService`, carrying only the execution ID per
  spec section 16. No dedicated queue library (no Spring AMQP, no Redis
  Streams/consumer groups) — a plain list is enough for one logical worker
  pool and keeps the dependency footprint small.
- **Enqueue happens in a transaction-commit callback**
  (`TransactionSynchronizationManager.registerSynchronization(...).afterCommit`)
  inside `ExecutionService`, not immediately after `save()`. Pushing to
  Redis before the DB transaction commits would let a worker pop the
  execution ID and query for a row that isn't visible yet on another
  connection (READ COMMITTED). This applies to both manual (`triggerManual`)
  and scheduled (`enqueueScheduled`) paths.
- **`ExecutionStatus` gains `RUNNING`/`SUCCESS`/`FAILED` only** —
  `CANCELLED`/`TIMEOUT`/`RETRYING` stay out until Phase 4 adds the
  cancellation/timeout/retry machinery that would ever produce them. Same
  reasoning Phase 2 used for trigger types and job types.
- **`timeoutSeconds`, `maxConcurrency`, and `retryPolicy` remain completely
  unenforced** in the worker — they're stored on `Job` but Phase 3's
  executors don't read them. A runaway shell command or a hanging HTTP
  request can occupy a worker thread indefinitely; that's accepted for now
  and is exactly the gap Phase 4 ("Reliability") is scoped to close. Do not
  add partial timeout handling to one job type without the other — do both
  together in Phase 4 with the `TIMEOUT` status.
- **Logs are buffered in memory during execution and persisted once at the
  end** (`ExecutionService.complete(...)` bulk-inserts `ExecutionLog` rows),
  not streamed to the DB line-by-line. There's no live consumer yet (that's
  Phase 5's WebSocket/SSE work), so incremental writes would only add DB
  load without anyone watching. `ExecutionRunner` collects lines into a
  `CopyOnWriteArrayList` because stdout/stderr are read on two concurrent
  threads.
- **New `execution_logs` table uses a `bigserial` PK**, not `UUID` like every
  other table. Log lines are pure append-only, ordering-sensitive records —
  `order by id` gives correct insertion order for free, whereas UUIDv4 sorts
  randomly. This is a deliberate, isolated exception to the UUID convention,
  not a drift.
- **HTTP job "exit code" reuses the `executions.exit_code` column to store
  the HTTP status code.** There's no separate `http_status` column in the
  spec's schema (section 13), and adding one for a single job type felt like
  more schema than the feature needs. `success` is still decided by the
  `ExecutionStatus` enum (2xx → `SUCCESS`), so `exitCode` is informational
  metadata either way.
- **No request/process timeout was added to either executor** (see above) —
  deliberately, to avoid a half-implemented version of Phase 4's timeout
  feature. `HttpClient.newHttpClient()` uses its infinite default; shell
  processes run via `waitFor()` with no deadline.
- **Worker identity is a random in-memory `UUID` generated at `WorkerService`
  startup** — there's no `workers` table yet (that's Phase 4's heartbeat /
  worker-registry concern, plus section 33's worker dashboard). It's stored
  on `executions.worker_id` purely as a "who ran this" breadcrumb.

## Environment surprises

- **No Redis was installed on this machine.** Installed via
  `brew install redis` and started with `brew services start redis` — took
  ~2s after `brew services start` reported success before `redis-cli ping`
  actually returned `PONG`; a script that pings immediately after
  `brew services start` needs a short retry, not a hard failure.
- **`./mvnw package` fails in this environment** with a `ClassNotFoundException:
  org.codehaus.plexus.archiver.FileSet` from the `maven-jar-plugin` — appears
  to be a corrupted/incomplete local plugin classpath unrelated to this
  phase's changes (compile, test-compile, and test all work fine offline).
  Worked around by using `./mvnw spring-boot:run` directly for manual
  verification instead of building a jar. Worth a `mvn -U` / clean `~/.m2`
  plugin cache pass if packaging is needed for deployment later.
- Confirmed (again, per Phase 2's note) that stale `java`/`next dev`
  processes were already running on 8080/3000 from before this session's
  changes — killed and restarted both before trusting manual verification.
- `com.redis:testcontainers-redis:2.2.4` resolved and worked immediately
  with Spring Boot's `@ServiceConnection` (same pattern as the existing
  Postgres container bean) — no version issues.
- As with Phase 2, **`@SpringBootTest` classes don't actually use
  Testcontainers** — they connect to the real local Postgres
  (`runway_test` db) per `application-test.properties`. The
  `TestcontainersConfiguration` class is only wired up through
  `TestApiApplication` (`SpringApplication.from(ApiApplication::main).with(...)`),
  which is the "run the app locally against ephemeral containers" dev
  helper, not the JUnit test path. So the new `redisContainer` bean added
  there is for symmetry with `postgresContainer` (keeps that dev-run helper
  working without a manually-managed Redis), but `WorkerIntegrationTest`
  actually needs a **real local Redis on `localhost:6379`** to pass, same as
  every other test needs a real local Postgres. Don't assume adding a
  Testcontainers bean makes a test self-contained in this repo — check
  which entry point actually loads `TestcontainersConfiguration` first.

## Frontend structure

- `app/(app)/executions/[id]/page.tsx`: new execution detail page — status,
  timing/exit-code metadata, error message (if any), and captured
  stdout/stderr rendered in a terminal-style `<pre>` block colored by
  stream (`STDOUT`/`STDERR`/`SYSTEM`).
- `ExecutionsTable.tsx`: status cell is now a colored link to the detail
  page (`text-blue-600` RUNNING, `text-green-600` SUCCESS, `text-red-600`
  FAILED) instead of plain text.
- `types.ts`: `ExecutionStatus` widened to include `RUNNING`/`SUCCESS`/
  `FAILED`; added `LogStream` and `ExecutionLog` types.
- No new server actions needed — the detail page reads directly via
  `apiFetch` (same pattern as the job detail page), no mutations happen
  there.

## Gotchas hit while testing

- Verified live via raw `curl` against a freshly-started API (worker +
  scheduler both enabled) rather than only unit tests: a `SHELL` job
  (`echo ... && sleep 1 && echo done`) went `QUEUED` → `RUNNING` → `SUCCESS`
  within ~1s of the manual trigger, with both stdout lines captured in
  order; a failing command (`exit 7`) produced `FAILED`/`exitCode: 7` with
  the stderr line captured; an `HTTP` job against a real external endpoint
  (`https://httpbin.org/status/200`) came back `SUCCESS`/`exitCode: 200`.
- Verified the frontend by setting the `runway_session` cookie directly to a
  JWT obtained from the raw API (Server Actions use an internal POST
  encoding that isn't easily driven by hand with `curl`) — no browser
  automation tool was available in this environment. The execution detail
  page rendered the `SUCCESS` status and captured log line correctly, and
  the job detail page's `ExecutionsTable` linked through to it.
- The `WorkerIntegrationTest` polls the DB in a bounded loop (up to 10s)
  waiting for `SUCCESS`/`FAILED` rather than using `Awaitility` (not a
  current dependency) — consistent with keeping the dependency list small
  for one lightweight polling need.

## Deferred / explicitly out of scope for Phase 3

Retries, timeouts (both job types), cancellation, concurrency enforcement
(the `max_concurrency` field is still just stored, not read), worker
heartbeats, a real `workers` table/registry (Phase 4); live log streaming /
WebSocket status updates — logs only appear once the execution finishes
(Phase 5); container/workflow job types; multiple concurrent worker
instances (nothing stops running two, but nothing was done to test or tune
for it beyond the `SKIP LOCKED` scheduler query and `BRPOP`'s natural
single-delivery semantics).

## Open question raised, not yet acted on

Same repo-split question flagged in Phase 2 notes — still one repo, not
revisited this phase. Separately: the `maven-jar-plugin` failure (see
Environment surprises) will need to be resolved before any containerization
or deployment work (Phase 4+ / section 41-42), since that needs a real
buildable artifact, not just `spring-boot:run`.
