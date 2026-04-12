package com.shopping.product.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for product data.
 * Decouples API response from JPA entity.
 */
public record ProductResponse(
    UUID id,
    String name,
    String description,
    BigDecimal price,
    String category,
    String imageUrl,
    Integer stock,
    String sku,
    BigDecimal rating,
    Boolean active,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
