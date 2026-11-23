package com.queueflow.service;

import java.util.List;
import com.queueflow.messaging.JobProducer.JobSubmitted;
import org.springframework.context.ApplicationEventPublisher;
import java.util.Optional;
import com.queueflow.dto.CreateJobRequest;
import com.queueflow.dto.JobResponse;
import com.queueflow.entity.Job;
import com.queueflow.repository.JobRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class JobService {
    private static final Logger log = LoggerFactory.getLogger(JobService.class);
    private final JobRepository repository;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transactions;

    public JobService(JobRepository repository, PlatformTransactionManager transactionManager, ApplicationEventPublisher events) {
        this.events = events;
        this.repository = repository;
        transactions = new TransactionTemplate(transactionManager);
        // Keep submission separate from any caller's transaction so a failed insert can roll back independently.
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public record Submission(JobResponse job, boolean created) {
    }

    public Submission submitJob(CreateJobRequest request) {
        try {
            return transactions.execute(transaction -> {
                Optional<Job> existing = repository.findByIdempotencyKey(request.idempotencyKey());
                if (existing.isPresent()) {
                    return matchingSubmission(existing.get(), request);
                }
                // Send the insert to PostgreSQL now so duplicate-key errors surface inside this transaction.
                Job job = repository.saveAndFlush(
                        new Job(request.type(), request.payload(), request.idempotencyKey()));
                // The RabbitMQ producer handles this event only after the database commit succeeds.
                events.publishEvent(new JobSubmitted(job.getId()));
                log.info("Created job {}", job.getId());
                return new Submission(JobResponse.from(job), true);
            });
        } catch (DataIntegrityViolationException exception) {
            if (!isIdempotencyKeyViolation(exception)) {
                throw exception;
            }
            // PostgreSQL rejects further queries in a failed transaction.
            // execute() finishes rolling it back before this fresh transaction reads the winning job.
            return transactions.execute(transaction -> repository
                    .findByIdempotencyKey(request.idempotencyKey())
                    .map(job -> matchingSubmission(job, request))
                    .orElseThrow(() -> exception));
        }
    }

    private boolean isIdempotencyKeyViolation(Throwable exception) {
        // 23505 means a unique constraint failed; check its name so unrelated database errors are not hidden.
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && "23505".equals(violation.getSQLState())
                    && "uk_jobs_idempotency_key".equals(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }

    private Submission matchingSubmission(Job job, CreateJobRequest request) {
        if (job.getType() != request.type() || !job.getPayload().equals(request.payload())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Idempotency key already used with different input");
        }
        return new Submission(JobResponse.from(job), false);
    }

    public JobResponse getJobById(Long id) {
        return repository.findById(id)
                .map(JobResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
    }

    public List<JobResponse> getAllJobs() {
        return repository.findAll().stream()
                .map(JobResponse::from).toList();
    }
}
