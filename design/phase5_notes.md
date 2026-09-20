# Phase 5 Notes

Working notes from building Phase 5 (Real-Time UI) of `full_spec.md` — SSE-based
live execution status, live logs, and live worker status. Meant to save a new
session from rediscovering the decisions and quirks below. The code and git
history are the source of truth; this is context that isn't visible from
reading the repo alone.

---

## Architecture decisions made

- **SSE over WebSocket.** All Phase 5 traffic is server→client only (the
  browser never sends anything after connecting; retry/cancel/run still go
  through the existing REST endpoints). `SseEmitter` ships in
  `spring-boot-starter-webmvc`, already a dependency, and the browser's native
  `EventSource` needs no library — a WebSocket/STOMP setup would have added
  real complexity for a purely one-directional need. Matches section 46's
  "prefer simple architecture first."
- **Scoped to two pages only: the execution detail page (live status + live
  logs) and the Workers page (live worker status).** The paginated/filterable
  executions list and the dashboard's "Recent Executions" widget stay
  server-rendered/static — making those live would need an org-wide
  multi-execution stream plus reconciling pagination/filtering, which is a
  meaningfully bigger feature than demonstrating the capability requires.
  Deferred, not forgotten.
- **Next.js Route Handlers proxy the SSE connections
  (`app/api/executions/[id]/stream/route.ts`, `app/api/workers/stream/route.ts`)
  rather than the browser talking to the Java API directly.** The JWT lives in
  an `httpOnly` cookie (`app/lib/session.ts`) readable only server-side, and
  browser `EventSource` can't set an `Authorization` header anyway. The route
  handler reads the cookie server-side, attaches `Bearer <token>`, and streams
  `upstream.body` straight through as `text/event-stream`. This is the first
  `app/api/**` Route Handler in the project — everything before this was
  Server Components + Server Actions calling `apiFetch` directly.
- **In-memory pub/sub, no Redis pub/sub.** Per Phase 3/4 notes the worker is
  still embedded in the same JVM as the API, so `ExecutionEventPublisher`
  (package `com.runway.api.executions`, next to `CancellationRegistry`) and
  `WorkerEventPublisher` (package `com.runway.api.worker`) are just
  `Map<UUID, List<SseEmitter>>` / `List<SseEmitter>` registries. Same
  single-instance caveat as `CancellationRegistry`: this does not fan out
  across multiple API instances. Revisit together whenever that changes.
- **SSE payloads reuse the existing REST DTOs verbatim**
  (`ExecutionResponse` for the `"execution"` event, `ExecutionLogResponse` for
  the `"log"` event, `WorkerResponse` for the `"worker"` event) — no new
  wire-format types, and `app/lib/types.ts` needed zero changes since the
  frontend already had `Execution`/`ExecutionLog`/`Worker` types matching
  those DTOs from Phases 3/4.
- **Publish calls live in the service/scheduler layer, not the entities** —
  right after each existing `mark*`/`heartbeat` call
  (`ExecutionService.markRunning/complete/cancel`, `RetryService.tick`'s
  `promoteToQueued`, `WorkerService.start/heartbeat/stop`,
  `WorkerHeartbeatReaper.tick`). Entities (`Execution`, `Worker`) stay pure
  state machines with no publisher dependency, consistent with how the rest of
  the codebase is layered.
- **"Live logs" required a real behavior change in `ExecutionRunner`**, not
  just new plumbing: the `Consumer<LogLine>` passed to `executor.execute(...)`
  previously only appended to the in-memory buffer that gets bulk-persisted at
  the end (Phase 3 decision — no live consumer existed yet). It now also calls
  `executionEventPublisher.publishLog(...)` per line as it's captured. Log
  rows are still only persisted to Postgres at completion (unchanged) — the
  live path is purely in-memory broadcast to anyone currently subscribed.
- **`ExecutionEventPublisher.complete(executionId)` closes and removes all
  emitters for an execution once a terminal status is published**, called
  right after publishing `SUCCESS`/`FAILED`/`TIMEOUT`/`CANCELLED` in
  `ExecutionService.complete()` and in the `cancel()` QUEUED branch. Confirmed
  live via curl: the connection closes on its own the moment the terminal
  event is sent, well before the emitter's own (disabled, `0L`) timeout would
  ever fire.
- **Client-side: only open an `EventSource` if the execution's initial status
  is non-terminal.** `ExecutionDetail.tsx` checks
  `TERMINAL_STATUSES.includes(initialExecution.status)` before subscribing —
  the large majority of execution-detail-page views are for already-finished
  executions, which render exactly as before with zero live connection
  opened.
- **No event replay/backfill.** If the SSE connection drops mid-execution and
  the browser's native `EventSource` auto-reconnects, anything published
  during the gap is missed (final state is still correct once a terminal
  event arrives or on next page load). Not solving at-least-once delivery
  here — same "simple architecture first" tradeoff as Phase 4's per-JVM
  cancellation.
- **`WorkerController.list()`/the new `/api/workers/stream` have no
  organization scoping** — matches the existing (lack of) scoping on
  `GET /api/workers` from Phase 4, not a new gap introduced here.

## Verification

- No good way to unit-test `SseEmitter` plumbing in isolation — `send()`
  buffers pre-handler with no observable delivery outside a real async
  request context, so a bare unit test can't assert what actually reached a
  subscriber. Matches how Phase 3/4 treated similarly runtime/IO-bound
  behavior (worker heartbeats, live process execution): verified live instead
  of forcing a brittle unit test. The existing test suite
  (`WorkerIntegrationTest`, `RetryServiceTest`, `WorkerHeartbeatReaperTest`,
  etc.) passes unmodified — none of this changes persisted behavior.
- Verified live via raw `curl -N` against the Java API directly: triggered a
  `sleep`-based shell job and watched `event:log`/`event:execution` SSE
  frames arrive with the exact JSON shape of the REST DTOs, and confirmed the
  connection closes on its own once the terminal `execution` event is sent.
  Same for `/api/workers/stream`.
- Verified the Next.js proxy routes with `curl -N` + a manually-set
  `runway_session` cookie (same JWT obtained from the raw API) — confirmed
  the pass-through isn't buffered (events arrive incrementally, not all at
  once at connection close) and the Bearer token is attached correctly.
- Verified end-to-end in a real browser via a scratch Playwright install (no
  browser automation tool preinstalled in this environment — same technique
  as Phases 3/4: inject the `runway_session` cookie directly with a JWT
  obtained from `curl`, since Server Actions aren't easily driven by hand).
  On the execution detail page: loaded mid-`RUNNING`, watched a log line
  appear and the status flip to `SUCCESS` with zero page reloads. On the
  Workers page: opened it while a worker was `HEALTHY`, triggered a job in
  the background via `curl`, and watched the row flip to `BUSY` live on the
  worker's own heartbeat tick — confirming the `WorkersTable` client
  component's `EventSource` wiring specifically, not just the initial SSR
  snapshot.
- One test-timing gotcha worth recording: a shell job's very first `echo`
  (e.g. `echo start`) can fire before a freshly-navigated page's
  `EventSource` has finished connecting, since logs aren't persisted
  incrementally (only at completion) and there's no backfill — so a
  just-loaded page can genuinely miss the first line. Not a bug; just don't
  write a verification script that asserts on the very first log line
  without a preceding `sleep` in the test job's command.

## Frontend structure

- `app/components/ExecutionDetail.tsx` (new, `"use client"`) — holds the
  JSX that used to be inline in `app/(app)/executions/[id]/page.tsx`, plus the
  `useEffect`-based `EventSource` subscription and local `execution`/`logs`
  state. The page itself is now a thin server component: fetch + `notFound()`
  handling + bound server actions, then render this component.
- `app/components/WorkersTable.tsx` (new, `"use client"`) — same shape for
  `app/(app)/workers/page.tsx`: table markup moved in, subscribes to
  `/api/workers/stream`, patches the matching row by `id` on each `"worker"`
  event (or prepends if it's a worker not seen yet).
- `RetryExecutionButton`/`CancelExecutionButton` needed no changes — bound
  server actions passed as props into a client component was already an
  established pattern from Phase 4, just now one level deeper
  (page → `ExecutionDetail` → button).

## Deferred / explicitly out of scope for Phase 5

Live updates on the paginated executions list and the dashboard's "Recent
Executions" widget (would need an org-wide multi-execution stream reconciled
with pagination/filtering — a bigger feature than demonstrating the
capability requires); SSE event replay/backfill after a dropped connection;
cross-instance broadcast (same single-JVM caveat as `CancellationRegistry`);
any reconnection backoff tuning beyond the browser's own default `EventSource`
retry behavior.
