package com.stockguard.dto;

import com.stockguard.entity.ReservationStatus;

import java.time.Instant;

public record ReservationResponse(
        Long id,
        Long productId,
        String userId,
        Integer qty,
        ReservationStatus status,
        Instant createdAt,
        Instant expiresAt
) {}
