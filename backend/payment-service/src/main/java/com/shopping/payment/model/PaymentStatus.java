package com.shopping.payment.model;

public enum PaymentStatus {
    INITIATED,
    PROCESSING,
    SUCCESS,
    FAILED,
    TIMEOUT,
    REFUNDED,
    PARTIALLY_REFUNDED,
    CANCELLED
}
