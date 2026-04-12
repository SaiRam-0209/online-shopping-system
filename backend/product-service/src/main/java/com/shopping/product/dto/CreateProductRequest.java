package com.shopping.product.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/**
 * DTO for creating a new product.
 * Enforces contract-first validation rules from OpenAPI spec.
 */
public record CreateProductRequest(
    @NotBlank(message = "Product name is required")
    @Size(min = 1, max = 200, message = "Product name must be between 1 and 200 characters")
    String name,

    @Size(max = 2000, message = "Description cannot exceed 2000 characters")
    String description,

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be at least 0.01")
    BigDecimal price,

    @NotBlank(message = "Category is required")
    String category,

    String imageUrl,

    @NotNull(message = "Stock is required")
    @Min(value = 0, message = "Stock cannot be negative")
    Integer stock,

    @NotBlank(message = "SKU is required")
    @Pattern(regexp = "^[A-Z]{2,4}-[0-9]{6}$", message = "SKU must match pattern: XX-000000 (e.g., ELEC-001234)")
    String sku
) {}
