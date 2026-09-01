package com.stockguard.dto;

import java.math.BigDecimal;

public record ProductResponse(
        Long id,
        String name,
        BigDecimal price,
        Integer availableQty,
        Integer reservedQty
) {}
