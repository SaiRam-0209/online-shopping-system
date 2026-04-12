package com.shopping.cart.controller;

import com.shopping.cart.dto.AddToCartRequest;
import com.shopping.cart.dto.UpdateCartItemRequest;
import com.shopping.cart.model.Cart;
import com.shopping.cart.service.CartService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for Cart Service.
 * Contract-first implementation aligned with cart-service-openapi.yaml.
 */
@RestController
@RequestMapping("/api/v1/cart")
@Validated
@CrossOrigin(origins = "*", maxAge = 3600)
public class CartController {

    private static final Logger log = LoggerFactory.getLogger(CartController.class);

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    /**
     * GET /api/v1/cart
     * Get current user's cart.
     */
    @GetMapping
    public ResponseEntity<Cart> getCart(@RequestHeader("X-User-Id") UUID userId) {
        log.info("GET /api/v1/cart - userId={}", userId);
        return ResponseEntity.ok(cartService.getCart(userId));
    }

    /**
     * POST /api/v1/cart/add
     * Add item to cart.
     */
    @PostMapping("/add")
    public ResponseEntity<Cart> addToCart(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody AddToCartRequest request) {
        log.info("POST /api/v1/cart/add - userId={}, productId={}", userId, request.productId());
        return ResponseEntity.ok(cartService.addToCart(userId, request));
    }

    /**
     * PUT /api/v1/cart/update
     * Update cart item quantity.
     */
    @PutMapping("/update")
    public ResponseEntity<Cart> updateCartItem(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody UpdateCartItemRequest request) {
        log.info("PUT /api/v1/cart/update - userId={}, productId={}", userId, request.productId());
        return ResponseEntity.ok(cartService.updateCartItem(userId, request));
    }

    /**
     * DELETE /api/v1/cart/remove
     * Remove item from cart.
     */
    @DeleteMapping("/remove")
    public ResponseEntity<Cart> removeFromCart(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam UUID productId) {
        log.info("DELETE /api/v1/cart/remove - userId={}, productId={}", userId, productId);
        return ResponseEntity.ok(cartService.removeFromCart(userId, productId));
    }

    /**
     * POST /api/v1/cart/apply-coupon
     * Apply coupon code to cart.
     */
    @PostMapping("/apply-coupon")
    public ResponseEntity<Cart> applyCoupon(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody Map<String, String> request) {
        String couponCode = request.get("couponCode");
        log.info("POST /api/v1/cart/apply-coupon - userId={}, code={}", userId, couponCode);
        return ResponseEntity.ok(cartService.applyCoupon(userId, couponCode));
    }

    /**
     * DELETE /api/v1/cart/clear
     * Clear entire cart.
     */
    @DeleteMapping("/clear")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearCart(@RequestHeader("X-User-Id") UUID userId) {
        log.info("DELETE /api/v1/cart/clear - userId={}", userId);
        cartService.clearCart(userId);
    }
}
