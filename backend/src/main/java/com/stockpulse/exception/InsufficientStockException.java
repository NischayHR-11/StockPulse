package com.stockpulse.exception;

import com.stockpulse.exception.ConflictException;

public class InsufficientStockException extends ConflictException {
    public InsufficientStockException(String message) {
        super(message);
    }
}
