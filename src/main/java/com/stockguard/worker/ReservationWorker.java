package com.stockguard.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockguard.config.RabbitMQConfig;
import com.stockguard.entity.Reservation;
import com.stockguard.entity.ReservationStatus;
import com.stockguard.event.ReservationCreatedEvent;
import com.stockguard.repository.ReservationRepository;
import com.stockguard.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@link RabbitMQConfig#WORK_QUEUE}. Messages reach this queue only
 * after the delay-queue TTL has elapsed (see {@link RabbitMQConfig}), so this
 * listener runs once a reservation's hold window has passed.
 *
 * <p>If the reservation is still PENDING it is expired and its stock released;
 * otherwise the event is a no-op.
 *
 * <p>Failure handling: exceptions are logged and swallowed rather than
 * requeued, since requeueing without a retry or dead-letter-on-failure policy
 * can lead to an infinite redelivery loop.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationWorker {

    private final ObjectMapper objectMapper;
    private final ReservationRepository reservationRepository;
    private final ReservationService reservationService;

    @RabbitListener(queues = RabbitMQConfig.WORK_QUEUE)
    public void handle(String messageBody) {
        try {
            ReservationCreatedEvent event = objectMapper.readValue(messageBody, ReservationCreatedEvent.class);
            process(event);
        } catch (Exception e) {
            log.error("Failed processing message, dropping it: {}", messageBody, e);
        }
    }

    private void process(ReservationCreatedEvent event) {
        Reservation reservation = reservationRepository.findById(event.reservationId()).orElse(null);
        if (reservation == null) {
            log.warn("Worker got event for reservation {} which no longer exists, skipping", event.reservationId());
            return;
        }
        if (reservation.getStatus() != ReservationStatus.PENDING) {
            log.info("Reservation {} already {}, nothing to do", reservation.getId(), reservation.getStatus());
            return;
        }
        log.info("Reservation {} still PENDING past TTL, expiring and releasing stock", reservation.getId());
        reservationService.expireIfPending(reservation.getId());
    }
}