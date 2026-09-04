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

    /** Time a PENDING reservation lives before it is expired and its stock returned. */
    private static final long RESERVATION_TTL_MINUTES = 5;

    private final ReservationRepository reservationRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductService productService;

    /**
     * Reserves stock for a product.
     *
     * <p>Idempotency is checked first, outside the inventory lock, so replays
     * of a completed request never touch inventory. A unique constraint on
     * {@code idempotency_key} is the safety net if two identical requests
     * race past the initial lookup.
     *
     * <p>Concurrency is handled with {@code SELECT ... FOR UPDATE} on the
     * inventory row, so transactions targeting the same product serialize
     * at the database level rather than within a single JVM.
     */
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
            // Another request with the same idempotency key inserted first.
            // Return the persisted row instead of decrementing stock twice.
            log.warn("Idempotency key race detected for key={}, returning existing row", idempotencyKey);
            return toResponse(reservationRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> dup));
        }

        productService.evict(request.productId());
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

    /**
     * Cancels a PENDING reservation and returns its stock to available_qty.
     * Locks the inventory row before mutating it, same as {@link #reserve}.
     */
    @Transactional
    public ReservationResponse cancel(Long id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ReservationNotFoundException(id));

        if (reservation.getStatus() == ReservationStatus.PENDING) {
            Inventory inventory = inventoryRepository.lockByProductId(reservation.getProductId())
                    .orElseThrow(() -> new IllegalStateException("Inventory missing for product " + reservation.getProductId()));
            inventory.setAvailableQty(inventory.getAvailableQty() + reservation.getQty());
            inventory.setReservedQty(inventory.getReservedQty() - reservation.getQty());
            inventoryRepository.save(inventory);
            productService.evict(reservation.getProductId());
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
        return toResponse(reservation);
    }

    private ReservationResponse toResponse(Reservation r) {
        return new ReservationResponse(r.getId(), r.getProductId(), r.getUserId(), r.getQty(),
                r.getStatus(), r.getCreatedAt(), r.getExpiresAt());
    }
}