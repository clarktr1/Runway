# Phase 1 Notes

Working notes from building Phase 1 (Foundation) of `full_spec.md` — meant to
save a new session from rediscovering the environment quirks and decisions
below. The code and git history are the source of truth; this is context that
isn't visible from reading the repo alone.

---

## Environment surprises

This machine's toolchain is ahead of training-data expectations in ways that
matter for any future backend or frontend work:

- **Spring Boot is on the 4.x line (we used 4.1.1), not 3.x.** Spring
  Initializr's `bootVersion` values from `/metadata/client` look like
  `4.1.1.RELEASE` but the actual Maven Central artifact is `4.1.1` (no
  `.RELEASE` suffix — that convention was dropped after 2.4.0). Always check
  `https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-starter-parent/maven-metadata.xml`
  if a generated `pom.xml` fails to resolve.
- **Spring Boot 4 split starters per-module** with matching `-test` variants
  (`spring-boot-starter-webmvc`, `spring-boot-starter-webmvc-test`,
  `spring-boot-starter-data-jpa-test`, etc.) instead of one catch-all
  `spring-boot-starter-test`.
- **`AutoConfigureMockMvc` moved packages**: it's now
  `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`,
  not `org.springframework.boot.test.autoconfigure.web.servlet...`.
- **Jackson 3 is the default JSON library**, under a new Maven group id
  `tools.jackson.core` (not `com.fasterxml.jackson.core`). Spring's
  autoconfigured `ObjectMapper` bean is `tools.jackson.databind.ObjectMapper`.
  `com.fasterxml.jackson...` (Jackson 2) can still be present transitively
  (e.g. via `jjwt-jackson`) but it's a *different, unrelated* class — don't
  mix them up in test code. Also: `JsonNode.asText()` is deprecated in favor
  of `asString()`, and Jackson 3 exceptions (`JacksonException`) are unchecked.
- **Next.js 16 renamed `middleware.ts` → `proxy.ts`** (same behavior/API,
  export can be named `proxy` or default). File lives at the repo root next
  to `app/`.
- The box had **no Maven, Gradle, or Docker installed**, only Java 22. We
  scaffolded the backend via `curl start.spring.io` (gives you the `mvnw`
  wrapper, no local Maven needed) and used **Homebrew Postgres** instead of
  Testcontainers/docker-compose for local dev + tests.

## Local environment setup

- Postgres 16 via `brew install postgresql@16`, running as a background
  service (`brew services start postgresql@16`).
- Databases: `runway` (dev), `runway_test` (used by the Spring `test`
  profile). Dedicated role: `runway` / password `runway` (superuser-ish via
  `ALTER DATABASE ... OWNER TO runway`) — not for production use, just local
  convenience.
- Backend config lives in `api/src/main/resources/application.properties`
  (dev) and `api/src/test/resources/application-test.properties` (test
  profile, points at `runway_test`). Both read `DB_USERNAME`/`DB_PASSWORD`/
  `RUNWAY_JWT_SECRET` from env vars with local-dev defaults baked in.
- Run backend: `cd api && ./mvnw spring-boot:run` (port 8080).
- Run frontend: `npm run dev` (port 3000, or next available — something else
  on this machine already squats on 3000 sometimes).
- Run backend tests: `cd api && ./mvnw test` — runs real MockMvc + Spring
  context tests against local `runway_test` Postgres (not Testcontainers,
  since Docker isn't installed). Swapping to Testcontainers later is a
  config-only change; `TestcontainersConfiguration`/`TestApiApplication`
  were left in place by Spring Initializr for that future switch.

## Architecture decisions made

- **Auth**: Spring Boot issues a signed JWT (HMAC-SHA256 via `jjwt`,
  BCrypt-hashed passwords). The Next.js server stores that JWT *directly* in
  an `httpOnly` cookie (no extra encryption layer — it's already a signed
  credential) and attaches it as `Authorization: Bearer` when calling the API
  from Server Components/Actions. The browser never talks to the API
  directly in Phase 1, so **no CORS config exists yet** — it'll be needed
  once something requires direct browser→API calls (e.g. WebSocket log
  streaming in Phase 5).
- **Data model**: `organizations` / `users` / `memberships` (role stored, not
  yet enforced beyond "is a member") / `jobs` (JSON `configuration` and
  `retry_policy` columns so Shell and HTTP job types share one table).
  Registering a user auto-creates a personal org + `OWNER` membership
  (single-org-per-user for now).
- **Job config validation** is a small manual check in `JobService`
  (`SHELL` requires `configuration.command`, `HTTP` requires `method`+`url`)
  rather than polymorphic DTOs — kept intentionally simple.
- No monorepo tooling — `api/` (Maven) and the root Next.js app are just two
  independent projects sharing a repo.

## Frontend structure

- `app/(app)/` route group holds the authenticated shell (nav + logout) and
  wraps `dashboard/`, `jobs/`, `jobs/new/`, `jobs/[id]/`. `verifySession()`
  in `app/lib/dal.ts` runs once in that layout.
- `app/lib/session.ts` (cookie get/set/delete), `app/lib/api.ts` (fetch
  wrapper + `ApiError` with field-level messages parsed from Spring's
  `ProblemDetail` responses), `app/lib/actions/{auth,jobs}.ts` (Server
  Actions).
- `app/components/JobForm.tsx` is shared between create and edit; a
  `confirmMessage` prop opts a form into a `window.confirm()` gate on submit
  (wired up for create, save, and delete — delete uses its own
  `DeleteJobButton` client component since the detail page itself is a
  Server Component and can't hold an `onSubmit` handler).
- `app/components/ScheduleField.tsx`: a schedule dropdown (common cron
  presets + "Custom…") with a live human-readable description via
  `cronstrue`. Note: `cronstrue.toString()` throws a **string**, not an
  `Error`, on invalid input — always wrap in try/catch.
- Color theme: palette tokens (`background #feffff`, `surface #def2f1`,
  `foreground #17252a`, `primary #3aafa9`, `primary-dark #2b7a78`) defined in
  `app/globals.css` via `@theme inline`. Dark-mode media query was
  **removed** (this is now the app's one fixed theme) — revisit if dark mode
  is wanted back. Destructive actions (delete) intentionally stay red,
  outside the palette.

## Gotchas hit while testing

- **Playwright's generic `button[type="submit"]` selector picked the wrong
  button** on pages wrapped by the `(app)` layout, because the layout's
  "Log out" button is a `type="submit"` button too and comes first in the
  DOM. Always scope test selectors by text (`button:has-text("Create Job")`)
  on any page with more than one form.
- The hydration-mismatch console warning about `style={{caret-color:
  "transparent"}}` on inputs is a Playwright/Chromium automation artifact,
  not a real app bug — safe to ignore in headless test runs.
- No `chromium-cli` available in this environment; browser verification used
  a scratch Playwright install instead (see the `run` skill's
  `examples/playwright.md` fallback).

## Deferred / explicitly out of scope for Phase 1

Scheduler, queue, workers, execution model (Phase 2/3); WebSockets/live logs
(Phase 5); RBAC enforcement beyond "is an org member", API keys, audit
logging, container jobs, workflows (post-MVP, section 44 of `full_spec.md`);
docker-compose / Testcontainers-backed integration tests (once Docker is set
up); "next run time" computation for the schedule picker (needs the actual
scheduler, not just cron parsing).
