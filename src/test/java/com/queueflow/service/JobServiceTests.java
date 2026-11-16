package com.queueflow.service;

import java.sql.SQLException;
import java.util.Optional;

import com.queueflow.dto.CreateJobRequest;
import com.queueflow.entity.Job;
import com.queueflow.entity.JobType;
import com.queueflow.repository.JobRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JobServiceTests {
    @Test
    void rollsBackBeforeReadingConcurrentWinnerInNewTransaction() {
        var repository = mock(JobRepository.class);
        var manager = mock(PlatformTransactionManager.class);
        var failed = new SimpleTransactionStatus();
        var recovery = new SimpleTransactionStatus();
        when(manager.getTransaction(any())).thenReturn(failed, recovery);
        var request = new CreateJobRequest(JobType.GENERATE_REPORT, "{}", "key");
        when(repository.findByIdempotencyKey("key")).thenReturn(Optional.empty(),
                Optional.of(new Job(request.type(), request.payload(), "key")));
        var violation = new ConstraintViolationException("duplicate",
                new SQLException("duplicate", "23505"), "uk_jobs_idempotency_key");
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate", violation));

        assertThat(new JobService(repository, manager).submitJob(request).created()).isFalse();

        var order = inOrder(manager, repository);
        order.verify(manager).getTransaction(any());
        order.verify(repository).findByIdempotencyKey("key");
        order.verify(repository).saveAndFlush(any());
        order.verify(manager).rollback(failed);
        order.verify(manager).getTransaction(any());
        order.verify(repository).findByIdempotencyKey("key");
        order.verify(manager).commit(recovery);
    }

    @Test
    void doesNotSwallowUnrelatedDatabaseConstraintFailure() {
        var repository = mock(JobRepository.class);
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(repository.findByIdempotencyKey("key")).thenReturn(Optional.empty());
        var violation = new ConstraintViolationException("other constraint",
                new SQLException("duplicate", "23505"), "some_other_constraint");
        var failure = new DataIntegrityViolationException("other constraint", violation);
        when(repository.saveAndFlush(any())).thenThrow(failure);

        assertThatThrownBy(() -> new JobService(repository, manager)
                .submitJob(new CreateJobRequest(JobType.GENERATE_REPORT, "{}", "key")))
                .isSameAs(failure);
        verify(repository, times(1)).findByIdempotencyKey("key");
        verify(manager).rollback(any());
    }
}
