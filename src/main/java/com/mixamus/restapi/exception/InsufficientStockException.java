package com.mixamus.restapi.exception;

public class InsufficientStockException extends ConflictException {

    public InsufficientStockException(String productName) {
        super("Not enough stock for product " + productName);
    }
}
