package com.shopping.order.controller;

import com.shopping.order.dto.CreateOrderRequest;
import com.shopping.order.dto.OrderResponse;
import com.shopping.order.model.OrderStatus;
import com.shopping.order.service.OrderService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for Order Service.
 * Handles order creation, status management, and order history.
 */
@RestController
@RequestMapping("/api/v1/orders")
@Validated
@CrossOrigin(origins = "*", maxAge = 3600)
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * POST /api/v1/orders
     * Create a new order from the user's cart.
     */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody CreateOrderRequest request) {
        log.info("POST /api/v1/orders - userId={}", userId);
        OrderResponse order = orderService.createOrder(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    /**
     * GET /api/v1/orders/{id}
     * Get order details by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID id) {
        log.info("GET /api/v1/orders/{}", id);
        return ResponseEntity.ok(orderService.getOrder(id));
    }

    /**
     * GET /api/v1/orders
     * Get user's order history.
     */
    @GetMapping
    public ResponseEntity<List<OrderResponse>> getUserOrders(
            @RequestHeader("X-User-Id") UUID userId) {
        log.info("GET /api/v1/orders - userId={}", userId);
        return ResponseEntity.ok(orderService.getUserOrders(userId));
    }

    /**
     * PUT /api/v1/orders/{id}/status
     * Update order status.
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @PathVariable UUID id,
            @RequestBody Map<String, String> request) {
        OrderStatus newStatus = OrderStatus.valueOf(request.get("status"));
        log.info("PUT /api/v1/orders/{}/status - newStatus={}", id, newStatus);
        return ResponseEntity.ok(orderService.updateOrderStatus(id, newStatus));
    }

    /**
     * POST /api/v1/orders/{id}/cancel
     * Cancel an order.
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable UUID id) {
        log.info("POST /api/v1/orders/{}/cancel", id);
        return ResponseEntity.ok(orderService.cancelOrder(id));
    }
}
