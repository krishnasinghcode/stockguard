package com.stockguard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockguard.config.RabbitMQConfig;
import com.stockguard.event.ReservationCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public void publishReservationCreated(Long reservationId) {
        try {
            String body = objectMapper.writeValueAsString(new ReservationCreatedEvent(reservationId));
            rabbitTemplate.convertAndSend(RabbitMQConfig.DELAY_QUEUE, body);
            log.info("Published ReservationCreated reservationId={} to delay queue", reservationId);
        } catch (Exception e) {
            log.error("Failed to publish ReservationCreated for reservationId={}", reservationId, e);
        }
    }
}