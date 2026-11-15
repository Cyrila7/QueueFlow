# QueueFlow progress

Last updated: November 14, 2026.

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

Original setup and Git history claims reflect the developer-provided status. For milestone 1, both existing containers were independently confirmed running with `docker compose ps`.

## Current implementation

The repository contains a Spring Boot application entry point and a scaffold `contextLoads()` test. Application properties configure PostgreSQL and RabbitMQ with local defaults and environment variable overrides, Hibernate `ddl-auto=update`, `open-in-view=false`, and Actuator health/info exposure with component status visibility. Maven declares Actuator, AMQP, JPA, validation, Spring MVC, PostgreSQL, and corresponding test dependencies; no dependencies were added for milestone 1.

Not yet implemented: job entity, enum, repository, service, or controller; RabbitMQ queues, producers, or consumers; retries, backoff, idempotency, or DLQ; transactional outbox; custom metrics; frontend; CI; application containerization.

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
2. [ ] Implement `Job`, `JobStatus`, and the repository.
3. [ ] Implement the core REST API.
4. [ ] Implement RabbitMQ asynchronous processing.
5. [ ] Implement retries, backoff, idempotency, and DLQ.
6. [ ] Implement the transactional outbox.
7. [ ] Add monitoring and automated tests (test important behavior throughout development).
8. [ ] Build the React and TypeScript dashboard.
9. [ ] Containerize application services.
10. [ ] Configure GitHub Actions.
11. [ ] Complete README and architecture documentation.
12. [ ] Consider optional AWS deployment after the core MVP.

Next feature, when explicitly requested: implement `Job`, `JobStatus`, and the repository.
