package com.queueflow.repository;

import java.util.Optional;

import com.queueflow.entity.Job;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRepository extends JpaRepository<Job, Long> {
    Optional<Job> findByIdempotencyKey(String idempotencyKey);
}
