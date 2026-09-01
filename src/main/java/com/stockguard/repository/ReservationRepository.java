package com.stockguard.repository;

import com.stockguard.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    // Used for idempotency: same key => same reservation, never insert twice.
    Optional<Reservation> findByIdempotencyKey(String idempotencyKey);
}
