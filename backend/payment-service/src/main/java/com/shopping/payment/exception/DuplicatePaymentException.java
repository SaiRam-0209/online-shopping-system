package com.shopping.payment.exception;

public class DuplicatePaymentException extends RuntimeException {
    public DuplicatePaymentException(String message) { super(message); }
}
