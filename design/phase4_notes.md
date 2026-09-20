# Phase 4 Notes

Working notes from building Phase 4 (Reliability) of `full_spec.md` — retries,
timeouts, cancellation, concurrency limits, and worker heartbeats. Meant to save a
new session from rediscovering the decisions and quirks below. The code and git
history are the source of truth; this is context that isn't visible from reading the
repo alone. The full design rationale (including alternatives considered) lives in
the plan that was approved for this phase; this file only covers what actually
landed plus gotchas hit along the way.

---

## Architecture decisions made

- **Each retry attempt is its own `Execution` row**, not a mutated row — `attempt`
  increments, `triggerType=RETRY`, same `job_id`. A pending automatic retry is a real
  row with `status=RETRYING` and `next_attempt_at` set, sitting outside Redis until a
  new `RetryService` ticker (same `@Scheduled`/`SKIP LOCKED` pattern as
  `SchedulerService`) promotes it to `QUEUED` and enqueues it. This matches the
  existing "every execution is retained, flat history table" design better than
  mutating one row across attempts would have.
- **`CancellationToken`/`CancellationRegistry` live in the `executions` package, not
  `worker`.** `worker` already depends on `executions` (imports `Execution`,
  `ExecutionService`, etc.); putting the cancellation types in `worker` instead would
  have made `executions.ExecutionService` depend on `worker` too (it needs the
  registry to signal a running execution), creating a package cycle. `executions` is
  the core domain, `worker` is the runtime executor that depends on it — keep that
  direction one-way.
- **Cancellation only works for an execution running on the same JVM instance that
  received the cancel request.** The registry is an in-memory
  `ConcurrentHashMap<UUID, CancellationToken>` — there's no cross-instance signaling
  yet. Fine today since there's exactly one embedded `WorkerService` per running API
  process (per Phase 3's decision); revisit once workers are a separate deployable.
- **Concurrency limit enforcement lives in `ExecutionRunner`, not the scheduler**: it
  checks `countByJob_IdAndStatus(job, RUNNING) >= maxConcurrency` right before
  `markRunning`, and if at capacity, sleeps ~500ms and re-`enqueue`s the same
  execution id rather than running it. **Important**: `WorkerService` currently runs
  exactly one poll thread per instance, so with only one embedded worker this code
  path is never naturally exercised end-to-end through the real queue — one thread
  can't pop a second execution while it's still blocked running the first. It only
  matters once there are multiple concurrent pollers (multiple worker threads/JVMs).
  It's still correct and worth having now, but the integration test for it
  (`WorkerIntegrationTest.concurrencyLimitBlocksASecondExecutionWhileTheFirstIsRunning`)
  deliberately calls `executionRunner.run(...)` directly from two threads instead of
  going through `triggerManual` + the real queue, to actually exercise the gate.
- **Timeout + cancellation share one mechanism per executor.** `ShellJobExecutor`
  binds the token to `process::destroyForcibly` and uses
  `process.waitFor(timeoutSeconds, SECONDS)` (or a ~365-day "unlimited" timeout when
  `job.timeoutSeconds` is null) — check `isCancelled()` *before* `!finished` when
  deciding the outcome, since a cancel also causes `destroyForcibly()` which makes
  `waitFor` return as if it "finished." `HttpJobExecutor` switched from
  `httpClient.send(...)` to `sendAsync(...)` so the returned `CompletableFuture` can
  be bound to the token (`future.cancel(true)`) and given a `future.get(timeout, ...)`
  deadline; `TimeoutException`/`CancellationException`/`ExecutionException` map to
  `TIMEOUT`/`CANCELLED`/`FAILED` respectively.
- **Worker heartbeats reuse `runway.scheduler.enabled`** for the new `RetryService`
  and `WorkerHeartbeatReaper` tickers (already `false` in the test profile) — no new
  test-config flag needed to keep background tickers quiet during `@SpringBootTest`s.
  `WorkerService`'s own heartbeat tick is separate and gated implicitly by
  `runway.worker.enabled` (the whole bean doesn't exist otherwise).
- **`WorkerService.busyCount`** (an `AtomicInteger`) is incremented/decremented around
  `executionRunner.run(...)` inside the poll loop, entirely inside `WorkerService` —
  deliberately not wired as a dependency from `ExecutionRunner` back into
  `WorkerService`, to avoid a circular bean reference.
- **Dead-worker recovery**: `WorkerHeartbeatReaper` ticks on the same interval as the
  scheduler, finds workers with `last_heartbeat_at` older than
  `runway.worker.offline-threshold-ms` (default 30s) and `status != OFFLINE`, marks
  them `OFFLINE`, and calls `ExecutionService.failStaleRunningExecutionsForWorker(id)`
  — which just calls the existing `complete(..., FAILED, ...)` per execution, so it
  automatically flows through the same retry-scheduling logic as any other failure.
  `WorkerService.@PreDestroy` also marks itself `OFFLINE` immediately on graceful
  shutdown, so the reaper's timeout-based detection is really only needed for crashes.

## Environment / implementation gotchas hit while testing

- **Test DB backdating a timestamp via raw JDBC needs `saveAndFlush`, not `save`,
  first.** `WorkerHeartbeatReaperTest` originally did
  `workerRepository.save(new Worker(...))` then a raw `jdbcTemplate.update(...
  set last_heartbeat_at = ...)` to simulate a stale heartbeat — but Hibernate hadn't
  actually flushed the INSERT yet (assigned-UUID `@Id`, write-behind), so the raw
  UPDATE silently affected zero rows and got clobbered when the deferred INSERT
  finally happened with the original (non-backdated) timestamp. Fixed by
  `saveAndFlush` before the raw JDBC backdate.
- **Local Redis is shared between the `test` Spring profile and manual `dev` runs** —
  there's no per-profile Redis DB separation in `application*.properties` (only
  Postgres gets a separate `runway_test` database). Running the test suite pushes
  real execution ids onto `runway:executions:queue`; if you then start
  `./mvnw spring-boot:run` for manual verification right after, its `WorkerService`
  will pop and discard those stale (test-DB-only) ids first — harmless (`ExecutionRunner`
  already no-ops on a missing execution) but can transiently show the worker as `BUSY`
  in `GET /api/workers` for the first few seconds. Not a bug; just check
  `redis-cli llen runway:executions:queue` if a fresh dev server looks unexpectedly busy.
- **Jackson 3's untyped `Map<String,Object>` deserialization of a jsonb column
  returns `Integer`/`Long` for whole numbers**, not `Double` — confirmed this holds
  for `retry_policy` fields (`maxAttempts`, `initialDelaySeconds`, etc.) round-tripped
  through Postgres jsonb, so `RetryPolicy.from(...)`'s
  `Integer.parseInt(String.valueOf(value))` / `Long.parseLong(...)` approach (matching
  the existing loose-typing precedent for `configuration` fields from Phase 1) works
  without needing `Number`-based coercion.
- Verified live end-to-end via `curl` (registered a user, created jobs, hit
  `/run`, `/cancel`, `/retry`, `/workers`) and via a scratch Playwright install (same
  technique as Phase 3: inject the `runway_session` cookie directly with a JWT
  obtained from `curl`, since Server Actions aren't easily driven by hand). Confirmed:
  cancelling a `sleep 30` execution turns it `CANCELLED` in ~0s instead of 30s; a
  1-second-timeout job running `sleep 5` turns `TIMEOUT` in ~1s; a job with
  `retryPolicy.maxAttempts=3` that always fails produces exactly 3 execution rows
  (attempts 1-3, `MANUAL` then `RETRY`/`RETRY`) and stops — no 4th row; two manual
  triggers of a `maxConcurrency=1` job show one `RUNNING`/one `QUEUED` mid-flight, both
  `SUCCESS` after; the Retry/Cancel buttons on the execution detail page show/hide
  correctly per status and actually work end-to-end in the browser.

## Frontend structure

- `JobForm.tsx` gained a "Retry Policy" `<fieldset>` (Max Attempts, Strategy
  select, Initial/Max Delay seconds) — `buildJobRequest` in `actions/jobs.ts` now
  sends real values instead of the previously-hardcoded `retryPolicy: {}`.
- `app/components/RetryExecutionButton.tsx` / `CancelExecutionButton.tsx` mirror the
  existing `RunJobButton`/`DeleteJobButton` pattern (plain form-action button vs.
  confirm-gated form-action button). Wired into
  `app/(app)/executions/[id]/page.tsx`, shown conditionally by status
  (`RETRYABLE_STATUSES`/`CANCELLABLE_STATUSES` arrays in that file).
- New `app/(app)/workers/page.tsx` (id/status/started/last-heartbeat table, same
  style as `ExecutionsTable`) + a "Workers" nav link in `app/(app)/layout.tsx`. No
  server action needed — read-only page.
- `types.ts`: `ExecutionStatus` gained `TIMEOUT`/`CANCELLED`/`RETRYING`, `TriggerType`
  gained `RETRY`, `Execution` gained `nextAttemptAt`, new `Worker`/`WorkerStatus` types.

## Deferred / explicitly out of scope for Phase 4

Live log streaming / WebSocket status updates (Phase 5 — logs still only appear once
an execution finishes, unchanged from Phase 3); a `DRAINING` worker state or any
graceful-drain flow (only `STARTING/HEALTHY/BUSY/OFFLINE` exist — draining implies a
deploy/restart workflow that doesn't exist yet); cross-instance cancellation (the
`CancellationRegistry` is per-JVM only); CPU/memory metrics on the worker heartbeat
(section 18's example shows them, but nothing collects OS-level stats yet — would
need JMX/OSHI, judged not worth it for this phase); RBAC enforcement of who can
cancel/retry (same "member-only, no role checks yet" state as every other phase);
container/workflow job types.
