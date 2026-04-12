package com.shopping.order.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateOrderRequest(
    @NotBlank(message = "Shipping address is required")
    String shippingAddress,

    @NotBlank(message = "Payment method is required")
    String paymentMethod
) {}
