package com.shopping.order.service;

import com.shopping.order.dto.CreateOrderRequest;
import com.shopping.order.dto.OrderResponse;
import com.shopping.order.exception.OrderNotFoundException;
import com.shopping.order.model.Order;
import com.shopping.order.model.OrderItem;
import com.shopping.order.model.OrderStatus;
import com.shopping.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Order Service - handles order creation, status management, and payment integration.
 * 
 * NOTE: This service is subject to AI code review (Use Case 4).
 * Known issues intentionally present for review demonstration:
 * - Some areas have higher cyclomatic complexity
 * - Payment integration uses deterministic logic (no AI)
 */
@Service
@Transactional(readOnly = true)
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final RestTemplate restTemplate;

    public OrderService(OrderRepository orderRepository, RestTemplate restTemplate) {
        this.orderRepository = orderRepository;
        this.restTemplate = restTemplate;
    }

    /**
     * Create a new order from the user's cart.
     * Flow: Validate cart → Create order → Initiate payment → Update inventory
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public OrderResponse createOrder(UUID userId, CreateOrderRequest request) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID must not be null");
        }
        if (request == null) {
            throw new IllegalArgumentException("Order request must not be null");
        }

        log.info("Creating order for user: {}", userId);

        // Step 1: Fetch and validate cart
        Map<String, Object> cart = fetchCart(userId);
        List<Map<String, Object>> cartItems = (List<Map<String, Object>>) cart.get("items");

        if (cartItems == null || cartItems.isEmpty()) {
            throw new IllegalStateException("Cannot create order from empty cart");
        }

        // Step 2: Validate each item's stock
        for (Map<String, Object> cartItem : cartItems) {
            String productId = cartItem.get("productId").toString();
            int quantity = ((Number) cartItem.get("quantity")).intValue();
            validateProductStock(productId, quantity);
        }

        // Step 3: Create order entity
        Order order = new Order();
        order.setUserId(userId);
        order.setStatus(OrderStatus.PENDING);
        order.setShippingAddress(request.shippingAddress());
        order.setPaymentMethod(request.paymentMethod());
        order.setCreatedAt(LocalDateTime.now());

        // Step 4: Map cart items to order items
        List<OrderItem> orderItems = cartItems.stream()
            .map(ci -> {
                OrderItem item = new OrderItem();
                item.setProductId(UUID.fromString(ci.get("productId").toString()));
                item.setProductName((String) ci.get("productName"));
                item.setQuantity(((Number) ci.get("quantity")).intValue());
                item.setPrice(new BigDecimal(ci.get("price").toString()));
                item.setLineTotal(new BigDecimal(ci.get("lineTotal").toString()));
                item.setOrder(order);
                return item;
            })
            .collect(Collectors.toList());

        order.setItems(orderItems);

        // Step 5: Calculate totals (deterministic — no AI)
        BigDecimal subtotal = orderItems.stream()
            .map(OrderItem::getLineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal discount = cart.get("discount") != null 
            ? new BigDecimal(cart.get("discount").toString()) 
            : BigDecimal.ZERO;
        
        BigDecimal tax = subtotal.multiply(new BigDecimal("0.08")); // 8% tax — configurable
        BigDecimal total = subtotal.subtract(discount).add(tax);

        order.setSubtotal(subtotal);
        order.setDiscount(discount);
        order.setTax(tax);
        order.setTotal(total);

        // Step 6: Save order
        Order saved = orderRepository.save(order);
        log.info("Order created: id={}, total={}", saved.getId(), saved.getTotal());

        // Step 7: Initiate payment (async in production, sync here for simplicity)
        try {
            initiatePayment(saved.getId(), total, request.paymentMethod());
            saved.setStatus(OrderStatus.PAYMENT_PENDING);
            orderRepository.save(saved);
        } catch (Exception e) {
            log.error("Payment initiation failed for order {}: {}", saved.getId(), e.getMessage());
            saved.setStatus(OrderStatus.PAYMENT_FAILED);
            orderRepository.save(saved);
        }

        // Step 8: Clear cart after successful order creation
        clearCart(userId);

        return toResponse(saved);
    }

    /**
     * Get order by ID.
     */
    public OrderResponse getOrder(UUID orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("Order ID must not be null");
        }

        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        return toResponse(order);
    }

    /**
     * Get order history for a user.
     */
    public List<OrderResponse> getUserOrders(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID must not be null");
        }

        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId)
            .stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    /**
     * Update order status.
     * Validates state transitions to prevent invalid status changes.
     */
    @Transactional
    public OrderResponse updateOrderStatus(UUID orderId, OrderStatus newStatus) {
        if (orderId == null) {
            throw new IllegalArgumentException("Order ID must not be null");
        }
        if (newStatus == null) {
            throw new IllegalArgumentException("New status must not be null");
        }

        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        // Validate status transition
        if (!isValidTransition(order.getStatus(), newStatus)) {
            throw new IllegalStateException(
                "Invalid status transition: " + order.getStatus() + " → " + newStatus
            );
        }

        order.setStatus(newStatus);
        order.setUpdatedAt(LocalDateTime.now());
        Order updated = orderRepository.save(order);

        log.info("Order {} status updated: {} → {}", orderId, order.getStatus(), newStatus);
        return toResponse(updated);
    }

    /**
     * Cancel an order.
     * Only allowed for PENDING or PAYMENT_PENDING orders.
     */
    @Transactional
    public OrderResponse cancelOrder(UUID orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("Order ID must not be null");
        }

        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        if (order.getStatus() != OrderStatus.PENDING && 
            order.getStatus() != OrderStatus.PAYMENT_PENDING) {
            throw new IllegalStateException(
                "Cannot cancel order in status: " + order.getStatus()
            );
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(LocalDateTime.now());
        Order cancelled = orderRepository.save(order);

        // Restore inventory (in production, this would be a saga compensation)
        restoreInventory(cancelled);

        log.info("Order cancelled: id={}", orderId);
        return toResponse(cancelled);
    }

    // ---- Private helpers ----

    private boolean isValidTransition(OrderStatus current, OrderStatus next) {
        Map<OrderStatus, Set<OrderStatus>> validTransitions = Map.of(
            OrderStatus.PENDING, Set.of(OrderStatus.PAYMENT_PENDING, OrderStatus.CANCELLED),
            OrderStatus.PAYMENT_PENDING, Set.of(OrderStatus.CONFIRMED, OrderStatus.PAYMENT_FAILED, OrderStatus.CANCELLED),
            OrderStatus.PAYMENT_FAILED, Set.of(OrderStatus.PAYMENT_PENDING, OrderStatus.CANCELLED),
            OrderStatus.CONFIRMED, Set.of(OrderStatus.SHIPPED),
            OrderStatus.SHIPPED, Set.of(OrderStatus.DELIVERED),
            OrderStatus.DELIVERED, Set.of(OrderStatus.RETURNED),
            OrderStatus.CANCELLED, Set.of(),
            OrderStatus.RETURNED, Set.of()
        );

        Set<OrderStatus> allowed = validTransitions.getOrDefault(current, Set.of());
        return allowed.contains(next);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchCart(UUID userId) {
        String url = "http://cart-service:8082/api/v1/cart";
        try {
            return restTemplate.getForObject(url, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch cart for user: " + userId, e);
        }
    }

    private void validateProductStock(String productId, int quantity) {
        String url = "http://product-service:8081/api/v1/products/" + productId;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> product = restTemplate.getForObject(url, Map.class);
            if (product != null) {
                int stock = ((Number) product.get("stock")).intValue();
                if (quantity > stock) {
                    throw new IllegalStateException(
                        "Insufficient stock for product " + productId + 
                        ". Available: " + stock + ", Requested: " + quantity
                    );
                }
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Could not validate stock for product {}: {}", productId, e.getMessage());
        }
    }

    private void initiatePayment(UUID orderId, BigDecimal amount, String paymentMethod) {
        String url = "http://payment-service:8084/api/v1/payments";
        Map<String, Object> paymentRequest = Map.of(
            "orderId", orderId,
            "amount", amount,
            "method", paymentMethod
        );
        restTemplate.postForObject(url, paymentRequest, Map.class);
    }

    private void clearCart(UUID userId) {
        String url = "http://cart-service:8082/api/v1/cart/clear";
        try {
            restTemplate.delete(url);
        } catch (Exception e) {
            log.warn("Failed to clear cart for user {}: {}", userId, e.getMessage());
        }
    }

    private void restoreInventory(Order order) {
        for (OrderItem item : order.getItems()) {
            try {
                String url = "http://product-service:8081/api/v1/products/" + item.getProductId();
                @SuppressWarnings("unchecked")
                Map<String, Object> product = restTemplate.getForObject(url, Map.class);
                if (product != null) {
                    int currentStock = ((Number) product.get("stock")).intValue();
                    Map<String, Object> update = Map.of("stock", currentStock + item.getQuantity());
                    restTemplate.put(url, update);
                }
            } catch (Exception e) {
                log.error("Failed to restore inventory for product {}: {}", 
                    item.getProductId(), e.getMessage());
            }
        }
    }

    private OrderResponse toResponse(Order order) {
        return new OrderResponse(
            order.getId(),
            order.getUserId(),
            order.getStatus().name(),
            order.getItems().stream().map(i -> Map.<String, Object>of(
                "productId", i.getProductId(),
                "productName", i.getProductName(),
                "quantity", i.getQuantity(),
                "price", i.getPrice(),
                "lineTotal", i.getLineTotal()
            )).collect(Collectors.toList()),
            order.getSubtotal(),
            order.getDiscount(),
            order.getTax(),
            order.getTotal(),
            order.getShippingAddress(),
            order.getPaymentMethod(),
            order.getCreatedAt(),
            order.getUpdatedAt()
        );
    }
}
