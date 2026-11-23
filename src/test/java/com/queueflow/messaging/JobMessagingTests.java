package com.queueflow.messaging;

import java.util.UUID;
import com.queueflow.dto.CreateJobRequest;
import com.queueflow.entity.Job;
import com.queueflow.entity.JobStatus;
import com.queueflow.entity.JobType;
import com.queueflow.repository.JobRepository;
import com.queueflow.service.JobService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class JobMessagingTests {
    private static final String QUEUE = "queueflow.jobs.test." + UUID.randomUUID();
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("queueflow.jobs.queue", () -> QUEUE);
    }

    @Autowired JobService service;
    @Autowired JobRepository repository;
    @Autowired RabbitTemplate rabbit;
    @Autowired RabbitListenerEndpointRegistry listeners;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ApplicationEventPublisher events;
    private final String key = UUID.randomUUID().toString();

    @BeforeEach
    void declareQueue() {
        new RabbitAdmin(rabbit.getConnectionFactory()).declareQueue(new Queue(QUEUE, true));
    }

    @AfterEach
    void cleanup() {
        listeners.stop();
        repository.findByIdempotencyKey(key).ifPresent(repository::delete);
        new RabbitAdmin(rabbit.getConnectionFactory()).deleteQueue(QUEUE);
    }

    @Test
    void publishesOnlyIdAfterCommitAndDoesNotPublishDuplicates() {
        CreateJobRequest request = new CreateJobRequest(JobType.GENERATE_REPORT, "{}", key);
        JobService.Submission submitted = service.submitJob(request);
        assertThat(repository.findById(submitted.job().id())).isPresent();
        assertThat(rabbit.receiveAndConvert(QUEUE, 3000)).isEqualTo(submitted.job().id().toString());
        assertThat(service.submitJob(request).created()).isFalse();
        assertThat(rabbit.receive(QUEUE, 200)).isNull();
    }

    @Test
    void rolledBackSubmissionEventDoesNotPublish() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            Job job = repository.saveAndFlush(new Job(JobType.GENERATE_REPORT, "{}", key));
            events.publishEvent(new JobProducer.JobSubmitted(job.getId()));
            assertThat(rabbit.receive(QUEUE, 200)).isNull();
            status.setRollbackOnly();
        });
        assertThat(repository.findByIdempotencyKey(key)).isEmpty();
        assertThat(rabbit.receive(QUEUE, 200)).isNull();
    }

    @Test
    void listenerCompletesPersistedJob() throws Exception {
        JobService.Submission submitted = service.submitJob(
                new CreateJobRequest(JobType.SEND_NOTIFICATION, "{}", key));
        for (org.springframework.amqp.core.MessageListenerContainer container
                : listeners.getListenerContainers()) {
            container.start();
        }
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        Job job;
        do {
            job = repository.findById(submitted.job().id()).orElseThrow();
            if (job.getStatus() == JobStatus.COMPLETED) {
                break;
            }
            Thread.sleep(50);
        } while (System.nanoTime() < deadline);
        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(job.getAttemptCount()).isEqualTo(1);
    }
}
