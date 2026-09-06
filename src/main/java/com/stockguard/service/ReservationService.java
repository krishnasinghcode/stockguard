package com.stockguard.service;

import com.stockguard.dto.ReservationRequest;
import com.stockguard.dto.ReservationResponse;
import com.stockguard.entity.Inventory;
import com.stockguard.entity.Reservation;
import com.stockguard.entity.ReservationStatus;
import com.stockguard.exception.OutOfStockException;
import com.stockguard.exception.ReservationNotFoundException;
import com.stockguard.repository.InventoryRepository;
import com.stockguard.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private static final long RESERVATION_TTL_MINUTES = 5;

    private final ReservationRepository reservationRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductService productService;
    private final ReservationEventPublisher eventPublisher;

    @Transactional
    public ReservationResponse reserve(ReservationRequest request, String idempotencyKey) {
        var existing = reservationRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Idempotent replay for key={} -> reservation={}", idempotencyKey, existing.get().getId());
            return toResponse(existing.get());
        }

        Inventory inventory = inventoryRepository.lockByProductId(request.productId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown product: " + request.productId()));

        if (inventory.getAvailableQty() < request.qty()) {
            throw new OutOfStockException(request.productId());
        }

        inventory.setAvailableQty(inventory.getAvailableQty() - request.qty());
        inventory.setReservedQty(inventory.getReservedQty() + request.qty());
        inventory.setUpdatedAt(Instant.now());
        inventoryRepository.save(inventory);

        Reservation reservation = new Reservation();
        reservation.setProductId(request.productId());
        reservation.setUserId(request.userId());
        reservation.setQty(request.qty());
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setIdempotencyKey(idempotencyKey);
        reservation.setExpiresAt(Instant.now().plus(RESERVATION_TTL_MINUTES, ChronoUnit.MINUTES));

        try {
            reservationRepository.save(reservation);
        } catch (DataIntegrityViolationException dup) {
            log.warn("Idempotency key race detected for key={}, returning existing row", idempotencyKey);
            return toResponse(reservationRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> dup));
        }

        productService.evict(request.productId());
        eventPublisher.publishReservationCreated(reservation.getId());

        return toResponse(reservation);
    }

    @Transactional(readOnly = true)
    public ReservationResponse get(Long id) {
        return toResponse(reservationRepository.findById(id)
                .orElseThrow(() -> new ReservationNotFoundException(id)));
    }

    @Transactional
    public ReservationResponse confirm(Long id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ReservationNotFoundException(id));
        reservation.setStatus(ReservationStatus.CONFIRMED);
        return toResponse(reservation);
    }

    @Transactional
    public ReservationResponse cancel(Long id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ReservationNotFoundException(id));

        if (reservation.getStatus() == ReservationStatus.PENDING) {
            releaseStock(reservation);
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
        return toResponse(reservation);
    }

    @Transactional
    public void expireIfPending(Long id) {
        Reservation reservation = reservationRepository.findById(id).orElse(null);
        if (reservation == null || reservation.getStatus() != ReservationStatus.PENDING) {
            return;
        }
        releaseStock(reservation);
        reservation.setStatus(ReservationStatus.EXPIRED);
    }

    private void releaseStock(Reservation reservation) {
        Inventory inventory = inventoryRepository.lockByProductId(reservation.getProductId())
                .orElseThrow(() -> new IllegalStateException("Inventory missing for product " + reservation.getProductId()));
        inventory.setAvailableQty(inventory.getAvailableQty() + reservation.getQty());
        inventory.setReservedQty(inventory.getReservedQty() - reservation.getQty());
        inventoryRepository.save(inventory);
        productService.evict(reservation.getProductId());
    }

    private ReservationResponse toResponse(Reservation r) {
        return new ReservationResponse(r.getId(), r.getProductId(), r.getUserId(), r.getQty(),
                r.getStatus(), r.getCreatedAt(), r.getExpiresAt());
    }
}