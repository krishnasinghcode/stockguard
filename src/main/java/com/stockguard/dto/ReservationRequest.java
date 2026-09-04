package com.stockguard.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReservationRequest(
        @NotNull Long productId,
        @NotBlank String userId,
        @NotNull @Min(1) Integer qty
) {}
