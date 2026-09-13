# Phase 2 Notes

Working notes from building Phase 2 (Scheduling) of `full_spec.md` — cron
expressions, the scheduler service, manual execution, and execution history.
Meant to save a new session from rediscovering the decisions and quirks
below. The code and git history are the source of truth; this is context
that isn't visible from reading the repo alone.

---

## Architecture decisions made

- **Cron parsing uses `com.cronutils:cron-utils` (UNIX definition), not
  Spring's built-in `CronExpression`.** Spring's `org.springframework.
  scheduling.support.CronExpression` requires 6 fields (seconds first) and
  rejects standard 5-field Unix cron like `0 2 * * *` — which is what
  `ScheduleField.tsx` and `cronstrue` on the frontend already assume. `
  CronService` wraps cron-utils and is the one place `next_run_at` gets
  computed (on job create/update in `JobService`, and after each fire in
  `SchedulerService`).
- **`next_run_at` is recomputed from the previous `next_run_at`, not from
  "now"**, when the scheduler advances a job — keeps hourly/daily jobs
  aligned to their original slot instead of drifting forward each tick.
- **Duplicate-execution prevention uses a native `SELECT ... FOR UPDATE SKIP
  LOCKED` query** (`JobRepository.findDueJobIdsForUpdate`) inside the
  scheduler's `@Transactional` tick, per spec section 15's requirement to
  support multiple scheduler instances safely. Simpler than introducing a
  Redis-based lock this early.
- **`SchedulerService` ticks every 5s** (`@Scheduled(fixedDelayString =
  "${runway.scheduler.interval-ms:5000}")`), gated by `@ConditionalOnProperty
  (runway.scheduler.enabled, default true)` so it can be (and is) disabled in
  the test profile — otherwise `@SpringBootTest` would boot a live ticking
  scheduler racing against `@Transactional` test rollbacks.
- **`ExecutionStatus` currently only has `QUEUED`, and `TriggerType` only has
  `SCHEDULED`/`MANUAL`.** This mirrors the existing `JobType` precedent from
  Phase 1 (only `SHELL`/`HTTP` exist yet, not `CONTAINER`/`WORKFLOW`): no
  worker exists to ever produce `RUNNING`/`SUCCESS`/`FAILED`/etc. until Phase
  3, so those enum values aren't added until something can actually set
  them. Same logic for `RETRY`/`API`/`WORKFLOW` trigger types.
- **Manual execution reuses the existing `PATCH /api/jobs/{id}` full-update
  endpoint for the enable/disable toggle** (frontend sends the complete
  current job payload with `enabled` flipped) rather than adding a dedicated
  `PATCH /api/jobs/{id}/enabled` endpoint — one less endpoint, no backend
  change needed for what the frontend already had all the data for.
- **Execution history is one `GET /api/executions` endpoint** with optional
  `jobId`/`status` query params and pagination, backed by a single JPQL
  `@Query` with null-coalescing `(:param is null or ...)` clauses — covers
  the job-detail "Recent Executions" view and the global `/executions` page
  without four separate repository methods.

## Environment surprises

- **Tailwind v4 moved `translate-x-*`/`scale-*`/`rotate-*` off the `transform`
  property onto the native CSS `translate`/`scale`/`rotate` properties.**
  `getComputedStyle(el).transform` reads `"none"` even when a `translate-x-4`
  utility is applied and working — check `getComputedStyle(el).translate`
  instead. `transition-transform` does correctly include `translate` in its
  `transition-property` list, so animations still work.
- **An absolutely-positioned element with no explicit `left`/`right` does
  not reliably sit at the edge of its `relative` parent.** The
  `JobEnabledToggle` knob had only `top-0.5` (no `left`); its horizontal
  static position fell back to the browser's static-position algorithm,
  skewed by the `<button>`'s default `text-align: center`, so it rendered
  off-center and `translate-x-4` pushed it past the track's right edge.
  Fix: always give a sliding-knob pattern an explicit `left-0.5` baseline
  and translate *from* that baseline (`translate-x-0` / `translate-x-4`),
  never rely on the implicit static position.
- Confirmed a stale `java` process and a stale `next dev` process were
  already running in the background (likely IDE-launched) on ports 8080/3000
  before any verification — both were running **old compiled code** without
  this phase's changes. Always check `lsof -i :<port>` and the process start
  time before trusting "the server responds" as proof a change works; kill
  and restart if the binary predates your edits.

## Frontend structure

- `app/lib/actions/executions.ts`: `runJobAction` (manual trigger, calls
  `POST /api/jobs/{id}/run`, revalidates job detail + `/executions`).
- `app/lib/actions/jobs.ts`: added `toggleJobEnabledAction` (flips `enabled`
  via the full-update endpoint, revalidates `/jobs` + job detail).
- `app/components/RunJobButton.tsx`, `ExecutionsTable.tsx`,
  `JobEnabledToggle.tsx` — the toggle calls its server action directly from
  an `onClick` inside `startTransition`, no `<form>` needed since there's no
  extra field data to carry.
- New `/executions` page (global history, status filter via search params);
  dashboard and job-detail pages both gained a "Recent Executions" section
  using the shared `ExecutionsTable`.
- Jobs list gained "Next Run" and a live enable/disable toggle column.

## Gotchas hit while testing

- Playwright's generic `button[type="submit"]` selector again picked the
  wrong element — this time the job form's `confirmMessage` prop meant
  submit triggers `window.confirm()`, which Playwright auto-dismisses by
  default (returns `false`), silently no-opping the submit. Needed
  `page.on("dialog", d => d.accept())` *and* a text-scoped selector
  (`form button:has-text("Create Job")`).
- `page.waitForLoadState("networkidle")` after a `startTransition`-wrapped
  Server Action call is not a reliable "the UI updated" signal — it can
  resolve before the RSC re-render lands, causing a second rapid click to
  race the first. Used an explicit short wait when double-toggling in tests.
- Verified the scheduler live (not just via test) by creating a `* * * * *`
  job through the raw API and polling: confirmed a `SCHEDULED`/`QUEUED`
  execution appears ~2s after the minute boundary (matches the 5s tick) and
  `next_run_at` advances by exactly one minute.

## Deferred / explicitly out of scope for Phase 2

Actually running jobs (worker process, Redis queue, stdout/stderr capture —
Phase 3); retries, timeouts, cancellation, concurrency enforcement, worker
heartbeats (Phase 4); live logs / WebSocket status (Phase 5); RBAC
enforcement of the "who can run/edit jobs" permission table (section 28,
post-MVP); date-range and worker filtering on execution history (no worker
concept yet); container/workflow job types.

## Open question raised, not yet acted on

Discussed splitting `api/` into its own repo now that frontend (Netlify) and
backend (Render) will deploy to different platforms — both platforms support
building from a subdirectory of a monorepo, so it's not required, but it was
flagged as the first genuinely strong reason to split (independent build
triggers per platform vs. atomic cross-cutting commits). No repo split has
been done; still one repo as of this commit.
