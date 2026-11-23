package com.queueflow.messaging;

import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    @Bean
    public Queue jobsQueue(@Value("${queueflow.jobs.queue:queueflow.jobs}") String queueName) {
        return new Queue(queueName, true);
    }
}
