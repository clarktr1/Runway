# Runway

[![CI](https://github.com/clarktr1/Runway/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/clarktr1/Runway/actions/workflows/ci.yml)

**Live app:** [runway-ri1u.onrender.com](https://runway-ri1u.onrender.com) ·
[try the demo](https://runway-ri1u.onrender.com/demo), no account needed

Runway is a self-hosted job scheduling and execution platform: cron-based
scheduling, manual triggers, retries, timeouts, concurrency limits, live
execution logs, and distributed workers — including running commands on
remote servers over SSH — built on Next.js and Spring Boot.

The product spec lives at [`design/full_spec.md`](design/full_spec.md).
Runway currently implements the full MVP described there (Phases 1–5) plus
SSH remote execution and account management; see
[Known limitations](#known-limitations) for what's intentionally not built
yet.

## Features

- **Job types**: shell commands, HTTP requests, and SSH commands against a
  saved remote host
- **Scheduling**: cron expressions with a calculated `next_run_at`, plus a
  manual "Run Now" that goes through the same queue as a scheduled run
- **Reliability**: configurable retries (fixed or exponential backoff),
  per-job timeouts, cancellation, per-job concurrency limits, worker
  heartbeats
- **Live UI**: execution status and logs stream over Server-Sent Events
  while a job runs, no polling
- **SSH remote execution**: paste a private key, register a remote host,
  verify its host key once (trust-on-first-use, pinned), then run commands
  on it through the same scheduling/retry/history pipeline as any other job
- **Demo**: a public, read-only page of ready-made jobs (HTTP GET/POST against
  httpbin.org, live shell output, retries, timeouts) that anyone can read
  about and run with one click. No account is created; the output streams
  live inside each job's card
- **Auth**: JWT-based sessions, organization-scoped data, self-service
  account management (change email/password)

## Architecture

```
┌──────────────┐  HTTP   ┌──────────────────────────┐
│   Next.js    │────────▶│       Spring Boot         │
│      UI      │         │                            │
└──────────────┘         │  Auth · Jobs · Executions  │
                          │  Scheduler · Worker        │
                          └─────────┬──────────┬───────┘
                                    │          │
                            ┌───────▼──┐   ┌───▼─────┐
                            │ Postgres │   │  Redis   │
                            │          │   │ (queue)  │
                            └──────────┘   └──────────┘
```

The scheduler, the worker poll loop, and the REST API all run inside the
same Spring Boot process — see [Architecture decisions](#architecture-decisions)
for why.

## Tech stack

| Layer    | Choice                                         |
| -------- | ----------------------------------------------- |
| Frontend | Next.js (App Router), TypeScript, Tailwind CSS   |
| Backend  | Spring Boot 4, Spring Security, Spring Data JPA  |
| Database | PostgreSQL, Flyway migrations                    |
| Queue    | Redis (list-based)                               |
| SSH      | [sshj](https://github.com/hierynomus/sshj)       |
| Auth     | Stateless JWT ([jjwt](https://github.com/jwtk/jjwt)) |

## Getting started

### With Docker Compose (recommended)

```bash
docker compose up --build
```

This starts Postgres, Redis, the API (`:8080`), and the web app (`:3000`).
Flyway applies all migrations automatically on API startup. Open
[http://localhost:3000](http://localhost:3000), register an account, and
you're in.

Postgres data persists in a named volume; `docker compose down -v` also
wipes it.

### Without Docker

You'll need a local Postgres and Redis reachable at their defaults (e.g. via
Homebrew: `brew services start postgresql@16 redis`), then:

```bash
# backend — from ./api
./mvnw spring-boot:run

# frontend — from the repo root, in another terminal
npm install
npm run dev
```

### Configuration

| Variable                                  | Default (dev only)                | Used by |
| ------------------------------------------ | ----------------------------------- | ------- |
| `DB_HOST` / `DB_USERNAME` / `DB_PASSWORD`   | `localhost` / `runway` / `runway`   | api     |
| `REDIS_HOST` / `REDIS_PORT`                 | `localhost` / `6379`                | api     |
| `RUNWAY_JWT_SECRET`                         | insecure dev default                | api     |
| `RUNWAY_CREDENTIALS_KEY`                    | insecure dev default (32-byte base64 AES key) | api — encrypts SSH private keys at rest |
| `RUNWAY_API_URL`                            | `http://localhost:8080`             | web     |

**Set `RUNWAY_JWT_SECRET` and `RUNWAY_CREDENTIALS_KEY` to real random values
before running this anywhere but your own machine** — `docker-compose.yml`
doesn't set them, so containers run on the checked-in dev defaults as-is.

### Deploying to Render

Runway is two separate services plus Postgres and Redis — not one container.
The Next.js web app has no Java in it (see `Dockerfile`), so it needs the
Spring Boot API (`api/Dockerfile`) running as its own service to talk to.

[`render.yaml`](render.yaml) defines all four as a
[Render Blueprint](https://render.com/docs/blueprint-spec): push it to your
repo, then in Render choose **New > Blueprint** and point it at the repo.
Everything is wired automatically except one manual step: after the first
deploy, open the `runway-api` service's Environment tab and set
`RUNWAY_CREDENTIALS_KEY` to the output of `openssl rand -base64 32` — it
can't be generated automatically because it must decode to exactly 32 bytes,
and the blueprint deliberately leaves it unset (`sync: false`) rather than
put an insecure default in front of anyone with the repo.

## Example: creating and running a job

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"name":"Ada","email":"ada@example.com","password":"correct-horse-battery"}' \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')

curl -s -X POST http://localhost:8080/api/jobs \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{
        "name": "Nightly Backup",
        "type": "SHELL",
        "configuration": {"command": "/opt/scripts/backup.sh"},
        "cronExpression": "0 2 * * *",
        "timeoutSeconds": 1800,
        "maxConcurrency": 1,
        "retryPolicy": {"maxAttempts": 3, "strategy": "EXPONENTIAL", "initialDelaySeconds": 30, "maxDelaySeconds": 600},
        "enabled": true
      }'

# or trigger any job right now instead of waiting for its schedule:
curl -s -X POST http://localhost:8080/api/jobs/{jobId}/run -H "Authorization: Bearer $TOKEN"
```

An `SSH_COMMAND` job takes `"configuration": {"remoteHostId": "<uuid>", "command": "..."}`.
Add the key and host first — from the Account page, or
`POST /api/ssh-credentials` and `POST /api/remote-hosts` — then call
`POST /api/remote-hosts/{id}/test-connection` once to pin the host's key
before any job against it can run.

## API reference

Every endpoint except `/api/auth/*` requires `Authorization: Bearer <token>`
and is scoped to the caller's organization.

| Resource        | Endpoints |
| ---------------- | --------- |
| Auth             | `POST /api/auth/register`, `POST /api/auth/login` |
| Account          | `GET /api/account`, `PATCH /api/account/email`, `PATCH /api/account/password` |
| Jobs             | `GET/POST /api/jobs`, `GET/PATCH/DELETE /api/jobs/{id}` |
| Executions       | `GET /api/executions` (filter by `jobId`/`status`, paginated), `GET /api/executions/{id}`, `GET /api/executions/{id}/logs`, `GET /api/executions/{id}/stream` (SSE), `POST /api/jobs/{jobId}/run`, `POST /api/executions/{id}/retry`, `POST /api/executions/{id}/cancel` |
| Workers          | `GET /api/workers`, `GET /api/workers/stream` (SSE) |
| SSH credentials  | `GET/POST /api/ssh-credentials`, `DELETE /api/ssh-credentials/{id}` |
| Remote hosts     | `GET/POST /api/remote-hosts`, `GET/PATCH/DELETE /api/remote-hosts/{id}`, `POST /api/remote-hosts/{id}/test-connection`, `POST /api/remote-hosts/{id}/repin` |

## Testing

```bash
# backend — needs Postgres + Redis reachable at the test profile's
# defaults (see api/src/test/resources/application-test.properties)
cd api && ./mvnw test

# frontend
npm run lint
npm run build   # also type-checks
```

The backend suite spins up an in-process SSH server (Apache MINA SSHD) to
exercise the real SSH executor — connection success/failure, timeouts,
cancellation, and host-key pinning/mismatch — without needing Docker or a
real remote box.

`.github/workflows/ci.yml` runs both suites, builds both Docker images
(without pushing them), and runs a Trivy filesystem/dependency scan on every
pull request against `main`.

## Architecture decisions

- **Scheduler, worker, and API share one process.** The spec describes
  independently deployable workers pulling from a Redis queue; the
  implementation is built to that seam (`JobExecutor` strategy interface,
  a Redis-backed queue, worker heartbeats) but the scheduler and worker poll
  loop currently run as `@Scheduled` tasks inside the same Spring Boot app
  as the API (toggle: `runway.scheduler.enabled` / `runway.worker.enabled`).
  Scaling out today means running more replicas of the `api` image, not a
  separate `worker` image.
- **Demo runs are anonymous and never stored.** `/api/demo/**` is the only
  unauthenticated part of the API. It runs a fixed catalog of jobs directly on
  the real executors and retry policy and streams the output back over SSE:
  no account, no token, no job or execution rows, and no queue or worker
  involved. A visitor can't create or change a job through it, and closing the
  page cancels the run.
- **SSH host-key trust is pinned only on an explicit action.** A scheduled
  job never establishes trust on its own — `POST /api/remote-hosts/{id}/test-connection`
  is the only way a host's fingerprint gets pinned. A job against an
  unpinned host fails closed rather than trusting whoever answers first.
- **SSH cancellation is best-effort.** Closing the local SSH channel stops
  the client side immediately, but unlike a local shell job (which gets a
  real `SIGKILL`), there's no guarantee the remote process actually dies.
- **Credentials are encrypted at rest with AES-256-GCM** and never appear in
  any API response after creation, not even to the user who created them.

## Known limitations

Not yet built, tracked against [`design/full_spec.md`](design/full_spec.md):

- No RBAC enforcement — `MembershipRole` exists on memberships, but any
  authenticated organization member can do anything today
- No API keys, audit logging, or container/workflow job types
- No dashboard charts (the spec calls for Recharts; not wired up yet)
- SSE fan-out is single-JVM — no cross-instance broadcast
- The demo endpoints (`/api/demo/**`) are unauthenticated. They can only run
  the five jobs fixed in code, but they are limited by a global cap of 5
  concurrent runs rather than a per-client rate limit, and the shell demo runs
  `sh -c` on the API host, so keep that in mind before exposing an instance
  publicly
- No cloud/Terraform deployment — Docker Compose only

## Project structure

```
api/     Spring Boot backend (Java 21)
app/     Next.js frontend (App Router)
design/  Product spec and per-phase build notes
```
