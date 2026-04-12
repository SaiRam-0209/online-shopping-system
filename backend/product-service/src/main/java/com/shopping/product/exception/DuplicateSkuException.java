package com.shopping.product.exception;

/**
 * Exception thrown when a product with the same SKU already exists.
 */
public class DuplicateSkuException extends RuntimeException {
    public DuplicateSkuException(String message) {
        super(message);
    }
}
