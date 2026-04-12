package com.shopping.order.model;

public enum OrderStatus {
    PENDING,
    PAYMENT_PENDING,
    PAYMENT_FAILED,
    CONFIRMED,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    RETURNED
}
