# QueueFlow project instructions

## Purpose and scope

QueueFlow is a lightweight distributed background job processing system demonstrating backend engineering, asynchronous processing, reliability, testing, and DevOps skills for software engineering internships. Keep it simple enough for an intern to understand, implement, and explain confidently.

**NEVER OVERCOMPLICATE ANYTHING.** Prioritize readability, simplicity, correctness, and maintainability.

## Technology stack

- Java 21, Spring Boot 4.1.2, and Maven.
- Spring Data JPA and Hibernate with PostgreSQL 17.
- RabbitMQ 4.
- Docker and Docker Compose.
- JUnit and integration tests; GitHub Actions for CI.
- React and TypeScript dashboard.
- Spring Boot Actuator and Micrometer; SLF4J and Logback.

AWS EC2, IAM, and CloudWatch are optional after the core MVP is complete. Add Redis only if a meaningful use case is identified. Do not introduce Kafka, Kubernetes, Prometheus, Grafana, or unnecessary microservices.

## Architecture and API

1. A client submits a job through the Spring Boot REST API.
2. The API persists the job in PostgreSQL as `PENDING`.
3. The job message is published to RabbitMQ.
4. A worker consumes the message and updates the job to `PROCESSING`.
5. On success, the worker updates the job to `COMPLETED` and acknowledges the message.
6. Retryable failures are retried with exponential backoff.
7. After the maximum attempt count is exhausted, mark the job `FAILED` and route the message to a configured dead-letter queue (DLQ).

Job statuses are `PENDING`, `PROCESSING`, `COMPLETED`, and `FAILED`.

Planned endpoints:

- `POST /api/jobs`: submit a job; return HTTP 202 Accepted with its ID.
- `GET /api/jobs/{id}`: retrieve job status.
- Add other endpoints only when needed for the dashboard.

## Reliability requirements

- Allow at most three total processing attempts, including the initial attempt.
- Use exponential backoff: five seconds before the second attempt, then ten seconds before the third.
- Persist `attemptCount` in PostgreSQL and preserve the same job ID across retries.
- Implement idempotent processing to prevent duplicate effects.
- Use manual RabbitMQ acknowledgments.
- Configure a DLQ for terminal failures.
- Check existing job state and attempt limits before processing redelivered messages.
- Do not claim exactly-once delivery.

### Transactional outbox

Implement basic submission and RabbitMQ integration first. Then introduce a lightweight transactional outbox to address the database/broker dual-write problem:

- Persist the job and outbox event in the same PostgreSQL transaction.
- Use a scheduled Spring Boot publisher for outstanding events.
- Mark events published only after successful broker confirmation and routing verification.
- Account for possible duplicate delivery through idempotent processing.

## Coding rules

- Use conventional Spring Boot controller, service, repository, and entity layers.
- Prefer constructor injection.
- Use explicit Java types instead of `var` for readability and learning.
- Avoid unnecessary abstractions and design patterns.
- Explain why any new dependency is needed.
- Do not introduce Lombok unless explicitly requested.
- Keep methods focused and names descriptive.
- Use meaningful application logs.
- Never hardcode production credentials.
- Test important behavior before marking a feature complete.
- Do not automatically commit or push changes without permission.

## Development workflow

- Implement one agreed feature at a time; do not implement future roadmap features unless explicitly requested.
- Explain the purpose of important changes and summarize files created or modified.
- Report test results accurately, including limitations, assumptions, and unresolved issues.
- Leave opportunities for the developer to complete hands-on exercises.
- Update `PROGRESS.md` after completed and verified milestones.
- Use `PROGRESS.md` for current status, remaining milestones, and local infrastructure details.

Target a resume-ready MVP by December 1, 2026, without sacrificing understanding or correctness.
