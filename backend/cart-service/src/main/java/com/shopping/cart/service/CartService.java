package com.shopping.cart.service;

import com.shopping.cart.dto.AddToCartRequest;
import com.shopping.cart.dto.UpdateCartItemRequest;
import com.shopping.cart.exception.CartItemNotFoundException;
import com.shopping.cart.exception.InsufficientStockException;
import com.shopping.cart.model.Cart;
import com.shopping.cart.model.CartItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Cart Service implementation.
 * Uses Redis for cart storage and REST calls to Product Service for validation.
 */
@Service
public class CartService {

    private static final Logger log = LoggerFactory.getLogger(CartService.class);
    private static final String CART_KEY_PREFIX = "cart:";
    private static final Duration CART_TTL = Duration.ofDays(7);

    private final RedisTemplate<String, Cart> redisTemplate;
    private final RestTemplate restTemplate;

    public CartService(RedisTemplate<String, Cart> redisTemplate, RestTemplate restTemplate) {
        this.redisTemplate = redisTemplate;
        this.restTemplate = restTemplate;
    }

    /**
     * Get the cart for a user. Creates empty cart if none exists.
     */
    public Cart getCart(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID must not be null");
        }

        String key = CART_KEY_PREFIX + userId;
        Cart cart = redisTemplate.opsForValue().get(key);

        if (cart == null) {
            log.info("Creating new empty cart for user: {}", userId);
            cart = new Cart(userId);
            saveCart(cart);
        }

        return cart;
    }

    /**
     * Add an item to the cart.
     * Validates product existence and stock via Product Service.
     */
    public Cart addToCart(UUID userId, AddToCartRequest request) {
        if (userId == null) throw new IllegalArgumentException("User ID must not be null");
        if (request == null) throw new IllegalArgumentException("Request must not be null");

        log.info("Adding to cart: userId={}, productId={}, qty={}", 
            userId, request.productId(), request.quantity());

        // Validate product exists and has stock (REST call to Product Service)
        Map<String, Object> product = validateProduct(request.productId());

        Cart cart = getCart(userId);

        // Check if item already in cart
        Optional<CartItem> existingItem = cart.getItems().stream()
            .filter(item -> item.getProductId().equals(request.productId()))
            .findFirst();

        if (existingItem.isPresent()) {
            // Update quantity
            CartItem item = existingItem.get();
            int newQty = item.getQuantity() + request.quantity();
            validateStock(product, newQty);
            item.setQuantity(newQty);
        } else {
            // Add new item
            validateStock(product, request.quantity());
            CartItem newItem = new CartItem(
                request.productId(),
                (String) product.get("name"),
                (String) product.get("imageUrl"),
                new BigDecimal(product.get("price").toString()),
                request.quantity()
            );
            cart.getItems().add(newItem);
        }

        cart.recalculate();
        saveCart(cart);

        log.info("Cart updated: userId={}, itemCount={}", userId, cart.getItemCount());
        return cart;
    }

    /**
     * Update the quantity of an item in the cart.
     */
    public Cart updateCartItem(UUID userId, UpdateCartItemRequest request) {
        if (userId == null) throw new IllegalArgumentException("User ID must not be null");
        if (request == null) throw new IllegalArgumentException("Request must not be null");

        Cart cart = getCart(userId);

        CartItem item = cart.getItems().stream()
            .filter(i -> i.getProductId().equals(request.productId()))
            .findFirst()
            .orElseThrow(() -> new CartItemNotFoundException(
                "Product " + request.productId() + " not found in cart"));

        // Validate stock for new quantity
        Map<String, Object> product = validateProduct(request.productId());
        validateStock(product, request.quantity());

        item.setQuantity(request.quantity());
        cart.recalculate();
        saveCart(cart);

        return cart;
    }

    /**
     * Remove an item from the cart.
     */
    public Cart removeFromCart(UUID userId, UUID productId) {
        if (userId == null) throw new IllegalArgumentException("User ID must not be null");
        if (productId == null) throw new IllegalArgumentException("Product ID must not be null");

        Cart cart = getCart(userId);

        boolean removed = cart.getItems().removeIf(
            item -> item.getProductId().equals(productId)
        );

        if (!removed) {
            throw new CartItemNotFoundException("Product " + productId + " not found in cart");
        }

        cart.recalculate();
        saveCart(cart);

        log.info("Item removed from cart: userId={}, productId={}", userId, productId);
        return cart;
    }

    /**
     * Apply a coupon code to the cart.
     */
    public Cart applyCoupon(UUID userId, String couponCode) {
        if (userId == null) throw new IllegalArgumentException("User ID must not be null");
        if (couponCode == null || couponCode.isBlank()) {
            throw new IllegalArgumentException("Coupon code must not be empty");
        }

        Cart cart = getCart(userId);

        // Simulate coupon validation (in production, call Coupon Service)
        BigDecimal discountAmount = calculateCouponDiscount(couponCode, cart.getSubtotal());

        cart.setCouponCode(couponCode);
        cart.setDiscount(discountAmount);
        cart.recalculate();
        saveCart(cart);

        log.info("Coupon applied: userId={}, code={}, discount={}", userId, couponCode, discountAmount);
        return cart;
    }

    /**
     * Clear all items from the cart.
     */
    public void clearCart(UUID userId) {
        if (userId == null) throw new IllegalArgumentException("User ID must not be null");

        String key = CART_KEY_PREFIX + userId;
        redisTemplate.delete(key);
        log.info("Cart cleared: userId={}", userId);
    }

    // ---- Private helpers ----

    private void saveCart(Cart cart) {
        String key = CART_KEY_PREFIX + cart.getUserId();
        redisTemplate.opsForValue().set(key, cart, CART_TTL);
    }

    /**
     * Validate product exists via Product Service REST call.
     * Uses retry logic for transient failures.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> validateProduct(UUID productId) {
        String url = "http://product-service:8081/api/v1/products/" + productId;

        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                Map<String, Object> product = restTemplate.getForObject(url, Map.class);
                if (product == null) {
                    throw new CartItemNotFoundException("Product not found: " + productId);
                }
                return product;
            } catch (CartItemNotFoundException e) {
                throw e; // Don't retry on 404
            } catch (Exception e) {
                log.warn("Product validation attempt {}/{} failed: {}", attempt, maxRetries, e.getMessage());
                if (attempt == maxRetries) {
                    throw new RuntimeException("Failed to validate product after " + maxRetries + " attempts", e);
                }
                try {
                    Thread.sleep(1000L * attempt); // Exponential backoff
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during retry", ie);
                }
            }
        }
        throw new RuntimeException("Unreachable");
    }

    private void validateStock(Map<String, Object> product, int requestedQuantity) {
        int stock = ((Number) product.get("stock")).intValue();
        if (requestedQuantity > stock) {
            throw new InsufficientStockException(
                "Insufficient stock. Available: " + stock + ", Requested: " + requestedQuantity
            );
        }
    }

    /**
     * Deterministic coupon discount calculation.
     * In production, this would call a dedicated Coupon Service.
     */
    private BigDecimal calculateCouponDiscount(String couponCode, BigDecimal subtotal) {
        // Fixed discount codes for demonstration
        return switch (couponCode.toUpperCase()) {
            case "SAVE10" -> subtotal.multiply(new BigDecimal("0.10"));
            case "SAVE20" -> subtotal.multiply(new BigDecimal("0.20"));
            case "FLAT50" -> new BigDecimal("50.00").min(subtotal);
            default -> throw new IllegalArgumentException("Invalid or expired coupon: " + couponCode);
        };
    }
}
