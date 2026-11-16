# QueueFlow progress

Last updated: November 16, 2026.

Target: resume-ready MVP by December 1, 2026, without sacrificing understanding or correctness.

Repository: https://github.com/Cyrila7/QueueFlow  
Initial commit: `76803d4`.

## Completed

- Spring Boot project generated; Java 21 and Maven verified; initial dependencies installed.
- Docker Desktop and Docker Compose installed.
- PostgreSQL 17 and RabbitMQ 4 configured in `compose.yaml`; both containers started successfully.
- PostgreSQL host port conflict resolved with host port 5433 mapped to container port 5432.
- RabbitMQ management dashboard verified.
- Git repository initialized; initial commit created and pushed to GitHub.
- Codex installed and repository inspection completed.
- Project instructions and progress documentation created.
- Milestone 1 verified: Spring Boot connects to PostgreSQL and RabbitMQ with environment-overridable local settings; Hibernate and Actuator configured.
- Milestone 2 verified: `Job`, `JobStatus`, `JobType`, and `JobRepository` implemented with PostgreSQL repository integration tests.
- Milestone 3 verified: validated request/response records, job service, and core REST API with safe idempotent submission.

Original setup and Git history claims reflect the developer-provided status. For milestone 1, both existing containers were independently confirmed running with `docker compose ps`.

## Current implementation

The repository contains a Spring Boot application entry point and a scaffold `contextLoads()` test. Application properties configure PostgreSQL and RabbitMQ with local defaults and environment variable overrides, Hibernate `ddl-auto=update`, `open-in-view=false`, and Actuator health/info exposure with component status visibility. Maven declares Actuator, AMQP, JPA, validation, Spring MVC, PostgreSQL, and corresponding test dependencies; no dependencies were added for milestone 1.

The `entity` package contains `Job`, `JobStatus`, and `JobType`; the `repository` package contains `JobRepository` with `findByIdempotencyKey`. Jobs use generated identity IDs, string enums, text payloads, PENDING/zero-attempt defaults, JPA lifecycle timestamps, and a database-enforced unique, non-null idempotency key. No dependencies were added for milestone 2.

Milestone 2 verification: `./mvnw -B test` passed all 4 tests (3 repository integration tests and the existing context test), with 0 failures/errors/skips. Tests verified persistence and retrieval, defaults, string enum storage, timestamp updates, and duplicate-key rejection against local PostgreSQL. Direct `psql` inspection confirmed Hibernate created `public.jobs` with all eight columns, identity primary key, timestamp columns, enum check constraints, and `uk_jobs_idempotency_key`. Test transactions rolled back; the table contained 0 rows afterward. These tests require an available PostgreSQL instance using the configured connection settings; identity sequence values may advance despite rollback. Non-failing Mockito/Java agent warnings remain.

Milestone 3 adds `CreateJobRequest` and `JobResponse` records, `JobService`, and `JobController`. POST /api/jobs returns 202 for creation, 200 for an identical duplicate, and 409 for reuse with different type or exact payload text. GET /api/jobs/{id} returns a job or 404; GET /api/jobs returns all jobs. Invalid requests return 400. Clients cannot set server-managed fields through the request DTO. New jobs remain PENDING with zero attempts.

Submission runs in an independent transaction. If PostgreSQL reports SQLSTATE 23505 for the named idempotency-key constraint, the failed insert transaction rolls back before a fresh transaction reads and compares the existing job. Other database failures are rethrown. No dependencies were added.

Milestone 3 verification: `./mvnw -B test` passed all 10 tests with 0 failures/errors/skips. Coverage includes service creation, duplicates/conflicts, retrieval/listing, MockMvc API status codes and validation, eight concurrent identical submissions using independent transactions resulting in one record, rollback-before-recovery ordering, and unrelated constraint-error propagation. Tests clean up only their UUID-keyed rows; existing repository tests roll back. The initial run failed because PostgreSQL and Docker Desktop were stopped; Docker Desktop and existing Compose services were started, then the full suite passed. Non-failing Mockito/Java agent warnings remain.

Limitations: tests require local PostgreSQL; listing is currently unpaginated; payload equality is exact string equality. Idempotent submission does not yet implement idempotent worker processing.

Milestone 3 readability refactor verified on November 16, 2026: replaced `var` in JobService/JobController with explicit types, simplified GET methods to call repository methods directly, and added beginner-friendly comments explaining submission transactions, flushing, rollback before recovery, and constraint checks. Submission transaction boundaries and API behavior are preserved. AGENTS.md now prefers explicit Java types. `./mvnw -B test` passed all 10 existing tests with 0 failures/errors/skips.

Not yet implemented: RabbitMQ queues, producers, or consumers; retries, backoff, idempotent processing, or DLQ; transactional outbox; custom metrics; frontend; CI; application containerization.

Verification: `./mvnw test` passed (1 test, 0 failures/errors/skips). `./mvnw -B spring-boot:run` started successfully. `/actuator/health` returned HTTP 200 with overall, `db`, and `rabbit` status `UP`; `/actuator/info` returned HTTP 200 with `{}` (no custom info configured). The verification application was stopped afterward; existing containers were left running. Maven required access outside the sandbox to its cache and local services. Tests emitted non-failing Mockito/Java dynamic-agent warnings.

## Local infrastructure

| Component | Local address/details |
| --- | --- |
| PostgreSQL | `localhost:5433`, database `queueflow` |
| RabbitMQ AMQP | `localhost:5672` |
| RabbitMQ management UI | `http://localhost:15672` |
| Development credentials | Defined in `compose.yaml` |
| Persistent storage | Named Docker volumes for both services |

## Remaining milestones

1. [x] Connect Spring Boot to PostgreSQL and RabbitMQ.
2. [x] Implement `Job`, `JobStatus`, `JobType`, and the repository.
3. [x] Implement the core REST API.
4. [ ] Implement RabbitMQ asynchronous processing.
5. [ ] Implement retries, backoff, idempotency, and DLQ.
6. [ ] Implement the transactional outbox.
7. [ ] Add monitoring and automated tests (test important behavior throughout development).
8. [ ] Build the React and TypeScript dashboard.
9. [ ] Containerize application services.
10. [ ] Configure GitHub Actions.
11. [ ] Complete README and architecture documentation.
12. [ ] Consider optional AWS deployment after the core MVP.

Next feature, when explicitly requested: implement RabbitMQ asynchronous processing.
