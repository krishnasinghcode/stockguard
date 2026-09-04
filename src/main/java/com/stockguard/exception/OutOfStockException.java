package com.stockguard.exception;

public class OutOfStockException extends RuntimeException {
    public OutOfStockException(Long productId) {
        super("OUT_OF_STOCK for product " + productId);
    }
}
