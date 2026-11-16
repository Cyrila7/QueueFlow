package com.queueflow.dto;

import java.time.Instant;
import com.queueflow.entity.Job;
import com.queueflow.entity.JobStatus;
import com.queueflow.entity.JobType;

public record JobResponse(Long id, JobType type, JobStatus status, String payload,
        int attemptCount, Instant createdAt, Instant updatedAt) {
    public static JobResponse from(Job job) {
        return new JobResponse(job.getId(), job.getType(), job.getStatus(), job.getPayload(),
                job.getAttemptCount(), job.getCreatedAt(), job.getUpdatedAt());
    }
}
