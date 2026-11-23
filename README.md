# QueueFlow

**An asynchronous job processing system built with Java and Spring Boot.**

QueueFlow is a backend engineering project I'm building to learn how applications handle background tasks reliably without making users wait for long-running operations.

Instead of processing everything immediately, the system is designed to accept jobs, track their status, and process them asynchronously.

## Tech Stack

- **Backend:** Java 21, Spring Boot
- **Database:** PostgreSQL
- **Message Broker:** RabbitMQ
- **Infrastructure:** Docker Compose
- **Planned:** React dashboard, automated testing, GitHub Actions

## How It Works

1. A client submits a job through a REST API.
2. Spring Boot validates the request and stores the job in PostgreSQL.
3. The API returns a response without waiting for background processing.
4. RabbitMQ will distribute jobs to background workers.
5. Workers will process jobs and update their status.

## Current Progress

- Spring Boot REST API for job submission and status tracking
- PostgreSQL persistence for job records
- Idempotency-key handling to prevent duplicate submissions
- Docker Compose setup for PostgreSQL and RabbitMQ

## What's Next

- RabbitMQ producer and worker integration
- Automatic retries and dead-letter queue handling
- React dashboard for monitoring jobs
- Automated tests and CI pipeline

## Why I Built This

I wanted to understand how real backend systems handle background work, duplicate requests, failures, and reliability.

QueueFlow gives me hands-on experience with asynchronous processing, message queues, databases, and backend system design.

---

**Status:** Under active development

**Developer:** Cyril Annoh
