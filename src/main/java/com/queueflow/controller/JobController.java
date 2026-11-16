package com.queueflow.controller;

import java.util.List;
import com.queueflow.dto.CreateJobRequest;
import com.queueflow.dto.JobResponse;
import com.queueflow.service.JobService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/jobs")
public class JobController {
    private final JobService service;

    public JobController(JobService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<JobResponse> submitJob(@Valid @RequestBody CreateJobRequest request) {
        JobService.Submission result = service.submitJob(request);
        return ResponseEntity.status(result.created() ? HttpStatus.ACCEPTED : HttpStatus.OK)
                .body(result.job());
    }

    @GetMapping("/{id}")
    public JobResponse getJobById(@PathVariable Long id) {
        return service.getJobById(id);
    }

    @GetMapping
    public List<JobResponse> getAllJobs() {
        return service.getAllJobs();
    }
}
