package com.queueflow.dto;

import com.queueflow.entity.JobType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateJobRequest(
        @NotNull JobType type,
        @NotBlank String payload,
        @NotBlank @Size(max = 255) String idempotencyKey) {
}
