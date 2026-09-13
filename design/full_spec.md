# Runway

## Distributed Job Scheduling & Execution Platform

**Status:** Proposed
**Version:** 1.0
**Project Type:** Full-stack portfolio / production-style systems project

---

# 1. Overview

Runway is a self-hosted, distributed job scheduling and execution platform.

The platform allows users to define jobs, schedule them using cron expressions, manually trigger executions, monitor execution history, inspect logs, configure retries and timeouts, and distribute workloads across a fleet of worker nodes.

The core system separates:

1. **Job definition** — what should run
2. **Scheduling** — when it should run
3. **Queueing** — what needs to execute
4. **Execution** — where/how it runs
5. **Observability** — what happened

The system should begin as a single-machine application and evolve naturally into a distributed architecture.

---

# 2. Goals

## Primary Goals

Runway should demonstrate strong proficiency in:

* Full-stack application development
* REST API design
* Modern frontend development
* Relational database design
* Distributed systems
* Background processing
* Job scheduling
* Queue-based architectures
* Concurrency
* Failure handling
* Authentication and authorization
* WebSockets / real-time communication
* Testing
* CI/CD
* Containerization
* Observability
* Cloud deployment

## Secondary Goals

The project should provide a polished developer experience comparable to a small commercial SaaS product.

The application should make it possible to:

* Create jobs
* Schedule jobs
* Execute jobs manually
* Execute jobs automatically
* View execution history
* View live output
* Retry failed executions
* Cancel running jobs
* Configure concurrency
* Configure retries
* Configure timeouts
* Manage workers
* Create job dependencies
* Monitor system health

---

# 3. Non-Goals

The initial implementation will NOT attempt to compete with:

* Kubernetes
* Airflow
* Temporal
* AWS Batch
* GitHub Actions
* Enterprise orchestration platforms

The goal is to demonstrate understanding of the underlying engineering concepts rather than reproduce an entire commercial product.

---

# 4. Target Users

## Developer

A developer who needs to schedule and monitor recurring workloads.

Example:

> Run my database backup every night at 2 AM.

## System Administrator

An administrator managing several workers and monitoring job health.

## Development Team

A team that needs shared job definitions, execution history, permissions, and operational visibility.

---

# 5. Core User Experience

After logging in, the user sees the dashboard.

```text
Runway

Overview

Jobs                    24
Running                  3
Failed (24h)             2
Successful (24h)       187

------------------------------------------------

Recent Executions

✓ Database Backup        14.2s
✓ Solr Reindex           42.7s
✗ API Health Check        2.1s
✓ Log Cleanup              8.4s
```

The user can select:

**Jobs → Create Job**

and configure:

```text
Name
Description
Type
Command
Schedule
Timeout
Retry Policy
Concurrency
Enabled
```

---

# 6. Job Types

Runway should support multiple execution types.

## 6.1 Shell Command

Example:

```bash
python /opt/scripts/backup.py
```

## 6.2 HTTP Request

Example:

```text
POST https://example.com/api/reindex
```

Configuration:

```text
Method
URL
Headers
Body
Timeout
```

## 6.3 Container

Example:

```text
Image: my-backup:latest
Command: /app/backup.sh
```

Container execution should be introduced after the core scheduler is stable.

## 6.4 Workflow

A workflow consists of multiple jobs/nodes connected together.

Example:

```text
Database Backup
       |
       v
Verify Backup
       |
       v
Upload to S3
       |
       v
Send Notification
```

Workflow support is an advanced feature.

---

# 7. Scheduling

Jobs may be scheduled using cron expressions.

Example:

```text
0 2 * * *
```

Meaning:

> Every day at 2:00 AM.

The UI should provide both:

* Cron expression input
* Human-readable schedule description

Example:

```text
Cron:
0 */6 * * *

Every 6 hours
Next run: 04:00 AM
```

The scheduler must calculate and persist the next expected execution time.

---

# 8. Manual Execution

Every enabled job should expose:

**Run Now**

Manual execution should follow the same queueing path as scheduled execution.

The HTTP request should NOT execute the job directly.

Instead:

```text
POST /api/jobs/{id}/run
        |
        v
Create Execution
        |
        v
Enqueue Job
        |
        v
Return execution ID
```

Example response:

```json
{
  "execution_id": "exec_18291",
  "status": "queued"
}
```

---

# 9. High-Level Architecture

```text
                         ┌─────────────────┐
                         │    Next.js UI   │
                         └────────┬────────┘
                                  │
                                  │ HTTPS
                                  ▼
                         ┌─────────────────┐
                         │   API Server    │
                         │                 │
                         │ Auth            │
                         │ Jobs            │
                         │ Executions      │
                         │ Workers         │
                         └───────┬─────────┘
                                 │
                    ┌────────────┼────────────┐
                    │            │            │
                    ▼            ▼            ▼
              PostgreSQL      Redis       WebSocket
                    │            │            │
                    │            ▼            │
                    │       Job Queue         │
                    │            │            │
                    │            ▼            │
                    │      ┌────────────┐      │
                    │      │  Scheduler │      │
                    │      └──────┬─────┘      │
                    │             │            │
                    │             ▼            │
                    │      ┌────────────┐      │
                    │      │  Workers   │──────┘
                    │      ├────────────┤
                    │      │ Worker 01  │
                    │      │ Worker 02  │
                    │      │ Worker 03  │
                    │      └────────────┘
                    │
                    └──────────────────────────
```

---

# 10. Technology Stack

## Frontend

* Next.js
* TypeScript
* React
* Tailwind CSS
* shadcn/ui
* Recharts

The frontend should communicate with the backend exclusively through documented APIs.

---

## Backend

Primary language:

**Java**

Framework:

**Spring Boot**

Libraries/components:

* Spring Web
* Spring Security
* Spring Data JPA
* PostgreSQL driver
* Redis client
* Bean Validation
* WebSocket support
* Testcontainers

Java is intentionally selected to demonstrate production-oriented backend development and complement existing Python/Django experience.

---

# 11. Database

Primary database:

**PostgreSQL**

Core tables:

```text
users
organizations
memberships
jobs
job_schedules
executions
execution_logs
workers
worker_heartbeats
api_keys
credentials
audit_events
```

---

# 12. Job Model

Conceptual job structure:

```text
Job
├── id
├── organization_id
├── name
├── description
├── type
├── configuration
├── enabled
├── timeout_seconds
├── max_concurrency
├── retry_policy
├── created_at
├── updated_at
└── next_run_at
```

The database should store execution-specific configuration separately from runtime state.

---

# 13. Execution Model

Each invocation of a job creates an execution.

```text
Execution
├── id
├── job_id
├── worker_id
├── trigger_type
├── status
├── attempt
├── queued_at
├── started_at
├── completed_at
├── exit_code
├── error_message
└── duration_ms
```

Possible statuses:

```text
QUEUED
RUNNING
SUCCESS
FAILED
CANCELLED
TIMEOUT
RETRYING
```

---

# 14. Trigger Types

Executions should record why they occurred.

```text
SCHEDULED
MANUAL
RETRY
API
WORKFLOW
```

This allows the dashboard to distinguish:

> "The job failed because of its normal schedule"

from:

> "A developer manually ran the job."

---

# 15. Scheduler

The scheduler is responsible for identifying jobs that are due to execute.

Conceptually:

```text
Every few seconds:

1. Find enabled jobs where next_run_at <= now
2. Acquire a lock
3. Create execution
4. Enqueue execution
5. Calculate next_run_at
6. Release lock
```

The scheduler must be designed to prevent duplicate execution if multiple scheduler instances are running.

Possible mechanisms:

* PostgreSQL row locking
* advisory locks
* Redis distributed locking

The initial implementation should use PostgreSQL locking.

---

# 16. Queue

The queue separates scheduling from execution.

```text
Scheduler
    |
    v
Redis Queue
    |
    +---- Worker 1
    |
    +---- Worker 2
    |
    +---- Worker 3
```

A queue message should contain a reference to an execution rather than the entire job configuration.

Example:

```json
{
  "execution_id": "exec_18291"
}
```

Workers retrieve the execution and load its associated job configuration.

---

# 17. Worker Architecture

Workers are independent processes.

A worker should:

1. Register with the API
2. Receive a worker ID
3. Send periodic heartbeats
4. Poll/subscribe to the queue
5. Claim an execution
6. Execute the job
7. Capture output
8. Update execution state
9. Report completion
10. Continue waiting

Worker state:

```text
STARTING
HEALTHY
BUSY
DRAINING
OFFLINE
```

---

# 18. Worker Heartbeats

Workers should periodically send heartbeats.

Example:

```text
worker-03

Status: HEALTHY
CPU: 31%
Memory: 48%
Jobs Running: 2
Last Heartbeat: 3 seconds ago
```

If a worker stops sending heartbeats, the server should eventually mark it offline.

---

# 19. Failure Handling

A worker can fail at any time.

Example:

```text
Execution #18291
        |
        v
Worker 02
        |
        X
   Worker crashes
```

The platform should detect that the worker disappeared and determine whether the execution should be requeued.

This requires an execution lease/heartbeat mechanism.

---

# 20. Retry Policy

Jobs should support configurable retry behavior.

Example:

```text
Maximum Attempts: 3
Strategy: Exponential Backoff
Initial Delay: 30 seconds
Maximum Delay: 10 minutes
```

Example execution:

```text
Attempt 1
   |
 FAILED
   |
 30 sec
   |
Attempt 2
   |
 FAILED
   |
 60 sec
   |
Attempt 3
```

After the final failure:

```text
FAILED
```

---

# 21. Timeout Handling

Each job may specify a maximum runtime.

Example:

```text
Timeout: 10 minutes
```

If the process exceeds the timeout:

```text
RUNNING
   |
   v
TIMEOUT
```

The worker should terminate the underlying process where possible.

---

# 22. Concurrency Control

Jobs may define:

```text
Maximum concurrent executions: 1
```

If the job is already running:

```text
Execution #100
RUNNING

Execution #101
QUEUED
```

The second execution cannot start until the concurrency limit allows it.

This prevents accidental overlapping operations such as:

```text
Backup
Backup
Backup
Backup
```

---

# 23. Live Logs

Workers should stream output while a job executes.

Example:

```text
Execution #18291

RUNNING

[22:41:01] Starting job
[22:41:02] Connecting to database
[22:41:04] Processing records
[22:41:06] Processing records
```

The frontend should receive updates using WebSockets or Server-Sent Events.

The system should support:

* stdout
* stderr
* timestamps
* log levels where available

---

# 24. Execution History

Every execution should be retained.

The UI should support:

* filtering
* sorting
* pagination
* status filtering
* date filtering
* execution duration
* worker filtering

Example:

```text
Executions

ID       Status     Duration   Worker
18291    SUCCESS    14.2s      worker-03
18290    FAILED      2.1s      worker-01
18289    SUCCESS    13.8s      worker-03
```

---

# 25. Retry From Failure

A user should be able to retry a failed execution.

The UI should provide:

**Retry**

Advanced functionality:

**Retry From Step**

This becomes relevant once workflow execution exists.

---

# 26. Cancellation

Running jobs should be cancellable.

```text
RUNNING
   |
   | Cancel
   v
CANCELLED
```

The worker should attempt to terminate the underlying process.

---

# 27. Authentication

Users should authenticate using secure session-based authentication or JWT-based authentication.

Required capabilities:

* Login
* Logout
* Password hashing
* Session/token management
* Password reset
* Account management

Authentication implementation should use established Spring Security patterns rather than custom cryptography.

---

# 28. Authorization

Runway should support organization-based access control.

Roles:

```text
OWNER
ADMIN
DEVELOPER
VIEWER
```

Example permissions:

| Action         | Owner | Admin | Developer | Viewer |
| -------------- | ----: | ----: | --------: | -----: |
| View jobs      |     ✓ |     ✓ |         ✓ |      ✓ |
| Create jobs    |     ✓ |     ✓ |         ✓ |        |
| Edit jobs      |     ✓ |     ✓ |         ✓ |        |
| Delete jobs    |     ✓ |     ✓ |           |        |
| Run jobs       |     ✓ |     ✓ |         ✓ |        |
| Manage workers |     ✓ |     ✓ |           |        |
| Manage members |     ✓ |     ✓ |           |        |
| View logs      |     ✓ |     ✓ |         ✓ |      ✓ |

Authorization must be enforced server-side.

---

# 29. API Keys

Runway should support API keys for automation.

Example:

```text
POST /api/v1/jobs/{id}/run
```

A user could trigger a job from another system:

```bash
curl -X POST \
  https://runway.example/api/v1/jobs/job_123/run \
  -H "Authorization: Bearer ..."
```

Only the hashed representation of API keys should be persisted.

---

# 30. Audit Logging

Important actions should generate audit events.

Examples:

```text
USER_CREATED_JOB
USER_UPDATED_JOB
USER_DELETED_JOB
JOB_MANUALLY_EXECUTED
JOB_RETRY_REQUESTED
WORKER_REGISTERED
MEMBER_ROLE_CHANGED
API_KEY_CREATED
```

Example:

```text
Trey
updated "Database Backup"
September 11, 2026 10:32 PM
```

---

# 31. Dashboard

The dashboard should provide operational visibility.

## Overview Metrics

```text
Total Jobs
Running
Queued
Successful
Failed
Workers
```

## Charts

* Executions over time
* Success/failure rate
* Average execution duration
* P95 execution duration
* Queue depth
* Worker utilization

---

# 32. Job Detail Page

The job page should contain:

```text
Job Information
Schedule
Configuration
Retry Policy
Concurrency
Recent Executions
Execution Statistics
```

Primary action:

**Run Now**

Secondary actions:

* Edit
* Disable
* Clone
* Delete

---

# 33. Worker Dashboard

Example:

```text
Workers

worker-01    HEALTHY    2 jobs
worker-02    HEALTHY    0 jobs
worker-03    BUSY       4 jobs
worker-04    OFFLINE
```

Worker detail:

```text
worker-03

CPU: 38%
Memory: 51%
Uptime: 14d
Executions: 12,821

Current Jobs

Database Backup
Solr Reindex
```

---

# 34. Workflow Engine

This is an advanced feature.

A workflow is represented as a directed acyclic graph.

Example:

```text
          A
          |
     ┌────┴────┐
     ▼         ▼
     B         C
     │         │
     └────┬────┘
          ▼
          D
```

A workflow node should support:

* job execution
* HTTP request
* condition
* delay
* notification

The engine should determine which nodes are ready to execute based on dependency state.

---

# 35. Workflow State

Each workflow execution maintains persistent state.

Example:

```text
Workflow Execution #500

A   SUCCESS
B   SUCCESS
C   FAILED
D   BLOCKED
```

Retrying C should allow D to continue without rerunning A and B.

---

# 36. Security Considerations

The system must protect against arbitrary job execution.

Because Runway executes commands, the worker environment should be considered privileged.

For production-oriented execution:

* workers should run with restricted OS permissions
* jobs should run inside containers where possible
* resource limits should be applied
* network access should be configurable
* filesystem access should be restricted
* secrets should not be exposed in logs

The initial MVP may support shell commands only in a controlled development environment.

---

# 37. Secret Management

Jobs should reference credentials rather than embedding secrets.

Example:

```json
{
  "credential_id": "cred_123"
}
```

Credentials should be encrypted at rest.

The application should never display the plaintext credential after creation.

---

# 38. Observability

Runway should instrument itself.

Required telemetry:

### Logs

Structured JSON logs containing:

```text
timestamp
request_id
user_id
job_id
execution_id
worker_id
level
message
```

### Metrics

Examples:

```text
runway_jobs_total
runway_executions_total
runway_execution_duration_seconds
runway_queue_depth
runway_worker_count
runway_worker_heartbeat_age
runway_execution_failures_total
```

### Tracing

Trace:

```text
HTTP Request
    |
    ├── PostgreSQL
    |
    ├── Redis
    |
    └── Worker Execution
          |
          └── External API
```

OpenTelemetry should be used for instrumentation.

---

# 39. Testing Strategy

## Unit Tests

Test:

* cron parsing
* schedule calculations
* retry policies
* permission checks
* workflow DAG validation
* concurrency rules
* state transitions

## Integration Tests

Test:

* PostgreSQL repositories
* Redis queues
* scheduler
* worker communication
* execution lifecycle

Testcontainers should be used where appropriate.

## API Tests

Test:

```text
POST /jobs
GET /jobs
PATCH /jobs/{id}
DELETE /jobs/{id}
POST /jobs/{id}/run
POST /executions/{id}/retry
POST /executions/{id}/cancel
```

## End-to-End Tests

Example:

```text
Create account
      ↓
Create job
      ↓
Create schedule
      ↓
Trigger execution
      ↓
Worker executes
      ↓
Execution succeeds
      ↓
Dashboard displays result
```

---

# 40. CI/CD

GitHub Actions should run:

```text
Lint
   ↓
Unit Tests
   ↓
Integration Tests
   ↓
Build
   ↓
Container Image
   ↓
Security Scan
```

Pull requests should be blocked if required checks fail.

---

# 41. Containerization

Development environment:

```text
docker-compose.yml

├── frontend
├── api
├── scheduler
├── worker
├── postgres
└── redis
```

The application should be runnable locally with a minimal setup process.

Example:

```bash
docker compose up
```

---

# 42. Deployment

The initial production deployment should target AWS.

Possible architecture:

```text
                    Internet
                       |
                       v
                Load Balancer
                       |
                ┌──────┴──────┐
                ▼             ▼
              API 1         API 2
                │             │
                └──────┬──────┘
                       │
             ┌─────────┼─────────┐
             ▼         ▼         ▼
          Postgres   Redis     Workers
```

Terraform should define infrastructure.

The exact AWS services may evolve during implementation.

---

# 43. MVP

The MVP should NOT include everything.

### Phase 1 — Foundation

* Next.js application
* Spring Boot API
* PostgreSQL
* Authentication
* Job CRUD
* Basic dashboard

### Phase 2 — Scheduling

* Cron expressions
* Scheduler service
* Manual execution
* Execution history

### Phase 3 — Workers

* Redis queue
* Worker process
* Job execution
* stdout/stderr capture
* success/failure handling

### Phase 4 — Reliability

* retries
* timeouts
* cancellation
* concurrency limits
* worker heartbeats

### Phase 5 — Real-Time UI

* WebSocket/SSE
* live logs
* live execution status
* worker status

At this point the project is already a strong portfolio piece.

---

# 44. Advanced Features

After the MVP:

* Distributed workers
* Container execution
* DAG workflows
* Job dependencies
* Scheduled workflows
* API keys
* RBAC
* Audit logging
* Secret management
* OpenTelemetry
* Grafana dashboard
* Terraform deployment
* Worker autoscaling
* Execution replay
* Rate limiting
* Resource constraints
* Job priorities
* Dead-letter queue

---

# 45. Example Complete Workflow

A user creates:

```text
Name:
Nightly Database Backup

Schedule:
0 2 * * *

Type:
Shell

Command:
/opt/scripts/backup.sh

Timeout:
30 minutes

Retries:
3

Concurrency:
1
```

At 2:00 AM:

```text
Scheduler
    |
    v
Creates Execution #18291
    |
    v
Redis Queue
    |
    v
Worker 03
    |
    v
Runs backup.sh
    |
    ├── stdout → log stream
    ├── metrics → telemetry
    └── status → API
```

The dashboard immediately displays:

```text
Nightly Database Backup

RUNNING

Worker: worker-03
Started: 02:00:02
Duration: 4.3s

[02:00:03] Starting backup
[02:00:04] Dumping database
[02:00:06] Compressing
```

When complete:

```text
SUCCESS

Duration: 14.2s
Exit Code: 0
```

The execution is permanently recorded.

---

# 46. Design Principles

The implementation should follow these principles:

### API requests should be fast

Never perform long-running work inside an HTTP request.

### State should be durable

Execution state must survive process restarts.

### Workers should be disposable

Any worker should be replaceable without losing system state.

### Jobs should be idempotent where possible

The system should minimize the consequences of duplicate execution.

### Failure should be expected

Worker failures, network failures, queue failures, and job failures should all be treated as normal operating conditions.

### Observability should be built in

Logs, metrics, and traces should not be added after the system is finished.

### Prefer simple architecture first

Start with:

```text
API
Scheduler
Worker
PostgreSQL
Redis
```

Only introduce additional infrastructure when there is an engineering reason.

---

# 47. Portfolio Presentation

The README should emphasize the engineering problem rather than simply listing technologies.

Suggested opening:

> **Runway is a distributed job scheduling and execution platform built for developers who need reliable background workloads without managing a full orchestration platform.**
>
> Runway provides cron-based scheduling, manual execution, distributed workers, retries, timeouts, concurrency control, live execution logs, and workflow orchestration through a modern web interface.

The README should include:

* Architecture diagram
* Screenshots
* Local setup
* Example job
* API documentation
* Architecture decisions
* Testing strategy
* Deployment architecture
* Performance measurements
* Known limitations

---

# 48. Success Criteria

Runway is considered successful when it can demonstrate:

1. A user can create a scheduled job.
2. The scheduler reliably identifies when it should run.
3. The execution is placed on a queue.
4. A worker claims the execution.
5. The worker executes the job.
6. Logs appear in the dashboard.
7. Execution status updates in real time.
8. Failed jobs can automatically retry.
9. Jobs can be manually triggered.
10. Jobs can be cancelled.
11. Multiple workers can execute jobs concurrently.
12. Worker failure does not permanently lose queued work.
13. Execution history survives application restarts.
14. Permissions prevent unauthorized operations.
15. The entire system can be tested and deployed reproducibly.

---

# 49. Long-Term Vision

The eventual system should feel like:

**Cron + GitHub Actions + Celery + a lightweight workflow engine**

without attempting to reproduce all of their functionality.

The core differentiator is the combination of:

```text
Scheduling
    +
Distributed Execution
    +
Developer UX
    +
Observability
```

The final project should demonstrate not only the ability to build a full-stack application, but the ability to reason about **reliability, concurrency, distributed state, failure recovery, and operational software**.
