package com.shopping.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record OrderResponse(
    UUID id,
    UUID userId,
    String status,
    List<Map<String, Object>> items,
    BigDecimal subtotal,
    BigDecimal discount,
    BigDecimal tax,
    BigDecimal total,
    String shippingAddress,
    String paymentMethod,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
