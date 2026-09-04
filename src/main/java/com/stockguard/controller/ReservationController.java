package com.stockguard.controller;

import com.stockguard.dto.ReservationRequest;
import com.stockguard.dto.ReservationResponse;
import com.stockguard.service.ReservationService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    public ResponseEntity<ReservationResponse> reserve(
            @Valid @RequestBody ReservationRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(reservationService.reserve(request, idempotencyKey));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReservationResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.get(id));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<ReservationResponse> confirm(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.confirm(id));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ReservationResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.cancel(id));
    }
}