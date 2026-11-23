package com.queueflow.messaging;

import java.io.IOException;
import java.util.Optional;
import com.queueflow.entity.Job;
import com.queueflow.entity.JobStatus;
import com.queueflow.repository.JobRepository;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class JobWorker {
    private static final Logger log = LoggerFactory.getLogger(JobWorker.class);
    private final JobRepository repository;
    private final TransactionTemplate transactions;

    public JobWorker(JobRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        transactions = new TransactionTemplate(transactionManager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @RabbitListener(queues = "${queueflow.jobs.queue:queueflow.jobs}", ackMode = "MANUAL")
    public void process(String jobId, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            Long id = Long.valueOf(jobId);
            Boolean started = transactions.execute(transaction -> {
                Optional<Job> existing = repository.findById(id);
                if (existing.isEmpty() || existing.get().getStatus() != JobStatus.PENDING) {
                    return false;
                }
                Job job = existing.get();
                job.setStatus(JobStatus.PROCESSING);
                job.setAttemptCount(job.getAttemptCount() + 1);
                repository.saveAndFlush(job);
                return true;
            });
            if (Boolean.TRUE.equals(started)) {
                log.info("Simulating work for job {}", id);
                // A separate commit makes PROCESSING visible before work completes.
                transactions.executeWithoutResult(transaction -> {
                    Job job = repository.findById(id).orElseThrow();
                    job.setStatus(JobStatus.COMPLETED);
                    repository.saveAndFlush(job);
                });
            }
        } catch (RuntimeException exception) {
            log.error("Job message {} failed; rejecting without retry", jobId, exception);
            channel.basicNack(deliveryTag, false, false);
            return;
        }
        // Acknowledge only after the database changes have committed.
        channel.basicAck(deliveryTag, false);
    }
}
