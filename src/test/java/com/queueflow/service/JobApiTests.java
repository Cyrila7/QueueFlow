package com.queueflow.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.queueflow.dto.CreateJobRequest;
import com.queueflow.entity.JobStatus;
import com.queueflow.entity.JobType;
import com.queueflow.repository.JobRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class JobApiTests {
    @Autowired JobService service;
    @Autowired JobRepository repository;
    @Autowired MockMvc mvc;
    private final List<String> keys = new ArrayList<>();

    String key() {
        String key = UUID.randomUUID().toString();
        keys.add(key);
        return key;
    }

    @AfterEach
    void cleanup() {
        // Delete only rows belonging to this test, never unrelated developer data.
        for (String key : keys) {
            repository.findByIdempotencyKey(key).ifPresent(repository::delete);
        }
    }

    @Test
    void serviceCreatesRetrievesAndListsJobs() {
        var request = new CreateJobRequest(JobType.GENERATE_REPORT, "{}", key());
        var result = service.submitJob(request);
        assertThat(result.created()).isTrue();
        assertThat(result.job().status()).isEqualTo(JobStatus.PENDING);
        assertThat(result.job().attemptCount()).isZero();
        assertThat(result.job().createdAt()).isNotNull();
        assertThat(service.getJobById(result.job().id()).id()).isEqualTo(result.job().id());
        assertThat(service.getAllJobs()).extracting(job -> job.id()).contains(result.job().id());
        assertThat(service.submitJob(request).created()).isFalse();
        assertThat(service.submitJob(request).job().id()).isEqualTo(result.job().id());
        assertThatThrownBy(() -> service.submitJob(
                new CreateJobRequest(JobType.SEND_NOTIFICATION, "{}", request.idempotencyKey())))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(409));
        assertThatThrownBy(() -> service.getJobById(-1L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void apiSubmissionAndRetrieval() throws Exception {
        String key = key();
        String body = "{\"type\":\"GENERATE_REPORT\",\"payload\":\"{}\",\"idempotencyKey\":\"" + key + "\"}";
        mvc.perform(post("/api/jobs").contentType("application/json").content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.attemptCount").value(0))
                .andExpect(jsonPath("$.idempotencyKey").doesNotExist());
        long id = repository.findByIdempotencyKey(key).orElseThrow().getId();
        mvc.perform(post("/api/jobs").contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        mvc.perform(post("/api/jobs").contentType("application/json")
                .content(body.replace("GENERATE_REPORT", "SEND_NOTIFICATION")))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/jobs").contentType("application/json")
                .content(body.replace("\"{}\"", "\" {}\"")))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/jobs/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
        mvc.perform(get("/api/jobs/-1")).andExpect(status().isNotFound());
        mvc.perform(get("/api/jobs")).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem((int) id)));
    }

    @Test
    void invalidRequestsReturnBadRequest() throws Exception {
        for (String body : List.of(
                "{}",
                "{\"type\":null,\"payload\":\"x\",\"idempotencyKey\":\"x\"}",
                "{\"type\":\"GENERATE_REPORT\",\"payload\":\" \",\"idempotencyKey\":\"x\"}",
                "{\"type\":\"GENERATE_REPORT\",\"payload\":\"x\",\"idempotencyKey\":\" \"}",
                "{\"type\":\"GENERATE_REPORT\",\"payload\":\"x\",\"idempotencyKey\":\"" + "x".repeat(256) + "\"}",
                "{\"type\":\"UNKNOWN\",\"payload\":\"x\",\"idempotencyKey\":\"x\"}",
                "{broken")) {
            mvc.perform(post("/api/jobs").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/jobs/not-a-number")).andExpect(status().isBadRequest());
    }

    @Test
    void concurrentIdenticalSubmissionsCreateOneRecord() throws Exception {
        var request = new CreateJobRequest(JobType.GENERATE_REPORT, "{}", key());
        var barrier = new CyclicBarrier(8);
        try (var executor = Executors.newFixedThreadPool(8)) {
            var futures = new ArrayList<java.util.concurrent.Future<JobService.Submission>>();
            for (int i = 0; i < 8; i++) {
                futures.add(executor.submit(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    // No outer test transaction: each service call uses independent transactions.
                    return service.submitJob(request);
                }));
            }
            var results = new ArrayList<JobService.Submission>();
            for (var future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            assertThat(results.stream().filter(JobService.Submission::created).count()).isEqualTo(1);
            assertThat(results).extracting(result -> result.job().id()).containsOnly(results.getFirst().job().id());
            assertThat(repository.findAll().stream()
                    .filter(job -> request.idempotencyKey().equals(job.getIdempotencyKey())).count()).isEqualTo(1);
        }
    }
}
