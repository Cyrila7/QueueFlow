package com.queueflow.messaging;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class JobProducer {
    public record JobSubmitted(Long jobId) {
    }

    private final RabbitTemplate rabbitTemplate;
    private final Queue jobsQueue;

    public JobProducer(RabbitTemplate rabbitTemplate, Queue jobsQueue) {
        this.rabbitTemplate = rabbitTemplate;
        this.jobsQueue = jobsQueue;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(JobSubmitted event) {
        // Send only the ID as text, without serializing the entity or payload.
        rabbitTemplate.convertAndSend("", jobsQueue.getName(), event.jobId().toString());
    }
}
