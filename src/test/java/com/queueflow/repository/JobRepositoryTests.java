package com.queueflow.repository;

import java.util.UUID;

import com.queueflow.entity.Job;
import com.queueflow.entity.JobStatus;
import com.queueflow.entity.JobType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class JobRepositoryTests {

    @Autowired
    private JobRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsAndRetrievesJobWithDefaultsAndStringEnums() {
        String key = UUID.randomUUID().toString();
        String payload = "{\"report\":\"" + "x".repeat(300) + "\"}";
        Job job = new Job(JobType.GENERATE_REPORT, payload, key);
        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(job.getAttemptCount()).isZero();

        repository.saveAndFlush(job);
        assertThat(job.getId()).isPositive();
        entityManager.clear();

        Job loaded = repository.findById(job.getId()).orElseThrow();
        assertThat(loaded.getType()).isEqualTo(JobType.GENERATE_REPORT);
        assertThat(loaded.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(loaded.getAttemptCount()).isZero();
        assertThat(loaded.getPayload()).isEqualTo(payload);
        assertThat(loaded.getIdempotencyKey()).isEqualTo(key);
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isEqualTo(loaded.getCreatedAt());
        assertThat(repository.findByIdempotencyKey(key)).contains(loaded);
        assertThat(repository.findByIdempotencyKey(UUID.randomUUID().toString())).isEmpty();

        Object[] stored = (Object[]) entityManager.createNativeQuery(
                "select type, status from jobs where id = :id")
                .setParameter("id", job.getId()).getSingleResult();
        assertThat(stored).containsExactly("GENERATE_REPORT", "PENDING");
    }

    @Test
    void updatesTimestampWithoutChangingCreationTime() {
        Job job = repository.saveAndFlush(new Job(
                JobType.SEND_NOTIFICATION, "{}", UUID.randomUUID().toString()));
        entityManager.clear();
        Job loaded = repository.findById(job.getId()).orElseThrow();
        var createdAt = loaded.getCreatedAt();
        var previousUpdatedAt = loaded.getUpdatedAt();

        loaded.setStatus(JobStatus.PROCESSING);
        loaded.setAttemptCount(1);
        repository.flush();
        entityManager.clear();

        Job updated = repository.findById(job.getId()).orElseThrow();
        assertThat(updated.getCreatedAt()).isEqualTo(createdAt);
        assertThat(updated.getUpdatedAt()).isAfter(previousUpdatedAt);
        assertThat(updated.getStatus()).isEqualTo(JobStatus.PROCESSING);
        assertThat(updated.getAttemptCount()).isEqualTo(1);
    }

    @Test
    void rejectsDuplicateIdempotencyKeyAtDatabaseLevel() {
        String key = UUID.randomUUID().toString();
        repository.saveAndFlush(new Job(JobType.GENERATE_REPORT, "{}", key));

        assertThatThrownBy(() -> repository.saveAndFlush(
                new Job(JobType.SEND_NOTIFICATION, "{}", key)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
