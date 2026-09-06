package com.stockguard.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the delay queue and work queue used for deferred reservation
 * processing.
 *
 * <p>Plain RabbitMQ queues have no native per-message delay, so this uses
 * the standard delay-queue + dead-letter pattern:
 *
 * <ol>
 *   <li>Publisher sends the message to {@link #DELAY_QUEUE}.</li>
 *   <li>Nothing consumes {@code DELAY_QUEUE} directly.</li>
 *   <li>Each message carries an {@code x-message-ttl}; once it expires,
 *       RabbitMQ dead-letters it to {@link #WORK_QUEUE}.</li>
 *   <li>The worker listens on {@code WORK_QUEUE}, so it only sees a message
 *       after the delay has elapsed.</li>
 * </ol>
 */
@Configuration
public class RabbitMQConfig {

    public static final String DELAY_QUEUE = "reservation.created.delay";
    public static final String WORK_QUEUE = "reservation.created";

    /** Must match ReservationService's RESERVATION_TTL_MINUTES (5 min). */
    private static final int DELAY_MS = 5 * 60 * 1000;

    @Bean
    public Queue delayQueue() {
        return QueueBuilder.durable(DELAY_QUEUE)
                .withArgument("x-message-ttl", DELAY_MS)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", WORK_QUEUE)
                .build();
    }

    @Bean
    public Queue workQueue() {
        return QueueBuilder.durable(WORK_QUEUE).build();
    }
}