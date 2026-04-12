package com.shopping.product.dto;

import java.math.BigDecimal;

/**
 * DTO for updating an existing product.
 * All fields are optional — only non-null fields are applied.
 */
public record UpdateProductRequest(
    String name,
    String description,
    BigDecimal price,
    String category,
    String imageUrl,
    Integer stock,
    Boolean active
) {}
