package com.shopping.cart;

import com.shopping.cart.dto.AddToCartRequest;
import com.shopping.cart.dto.UpdateCartItemRequest;
import com.shopping.cart.exception.CartItemNotFoundException;
import com.shopping.cart.exception.InsufficientStockException;
import com.shopping.cart.model.Cart;
import com.shopping.cart.model.CartItem;
import com.shopping.cart.service.CartService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 Test Suite for Cart Service
 * Covers: normal flow, edge cases, concurrent updates, and failure scenarios.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Cart Service Tests")
class CartServiceTest {

    @Mock
    private RedisTemplate<String, Cart> redisTemplate;

    @Mock
    private ValueOperations<String, Cart> valueOperations;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private CartService cartService;

    private UUID userId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        productId = UUID.randomUUID();
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ============================================================
    // NORMAL FLOW TESTS
    // ============================================================

    @Nested
    @DisplayName("Normal Flow - Get Cart")
    class GetCartTests {

        @Test
        @DisplayName("TC-CART-001: Get cart returns existing cart")
        void getCart_existingCart_returnsCart() {
            // Arrange
            Cart existingCart = new Cart(userId);
            existingCart.getItems().add(new CartItem(productId, "Test Product", null,
                new BigDecimal("29.99"), 2));
            existingCart.recalculate();

            when(valueOperations.get("cart:" + userId)).thenReturn(existingCart);

            // Act
            Cart result = cartService.getCart(userId);

            // Assert
            assertNotNull(result);
            assertEquals(userId, result.getUserId());
            assertEquals(1, result.getItems().size());
            assertEquals(new BigDecimal("59.98"), result.getSubtotal());
        }

        @Test
        @DisplayName("TC-CART-002: Get cart creates new cart when none exists")
        void getCart_noExistingCart_createsEmptyCart() {
            // Arrange
            when(valueOperations.get("cart:" + userId)).thenReturn(null);

            // Act
            Cart result = cartService.getCart(userId);

            // Assert
            assertNotNull(result);
            assertEquals(userId, result.getUserId());
            assertTrue(result.getItems().isEmpty());
            assertEquals(0, result.getItemCount());
        }
    }

    @Nested
    @DisplayName("Normal Flow - Add to Cart")
    class AddToCartTests {

        @Test
        @DisplayName("TC-CART-003: Add new item to empty cart")
        void addToCart_emptyCart_addsItem() {
            // Arrange
            Cart emptyCart = new Cart(userId);
            when(valueOperations.get("cart:" + userId)).thenReturn(emptyCart);

            Map<String, Object> product = Map.of(
                "id", productId.toString(),
                "name", "Wireless Headphones",
                "price", 79.99,
                "stock", 50,
                "imageUrl", "https://example.com/img.jpg"
            );
            when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(product);

            AddToCartRequest request = new AddToCartRequest(productId, 1);

            // Act
            Cart result = cartService.addToCart(userId, request);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.getItems().size());
            assertEquals("Wireless Headphones", result.getItems().get(0).getProductName());
            assertEquals(1, result.getItems().get(0).getQuantity());
        }

        @Test
        @DisplayName("TC-CART-004: Add existing item increments quantity")
        void addToCart_existingItem_incrementsQuantity() {
            // Arrange
            Cart cart = new Cart(userId);
            cart.getItems().add(new CartItem(productId, "Headphones", null,
                new BigDecimal("79.99"), 1));
            when(valueOperations.get("cart:" + userId)).thenReturn(cart);

            Map<String, Object> product = Map.of(
                "id", productId.toString(),
                "name", "Headphones",
                "price", 79.99,
                "stock", 50,
                "imageUrl", ""
            );
            when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(product);

            AddToCartRequest request = new AddToCartRequest(productId, 2);

            // Act
            Cart result = cartService.addToCart(userId, request);

            // Assert
            assertEquals(1, result.getItems().size());
            assertEquals(3, result.getItems().get(0).getQuantity());
        }
    }

    // ============================================================
    // EMPTY CART EDGE CASES
    // ============================================================

    @Nested
    @DisplayName("Edge Case - Empty Cart")
    class EmptyCartTests {

        @Test
        @DisplayName("TC-CART-005: Remove from empty cart throws exception")
        void removeFromCart_emptyCart_throwsException() {
            // Arrange
            Cart emptyCart = new Cart(userId);
            when(valueOperations.get("cart:" + userId)).thenReturn(emptyCart);

            // Act & Assert
            assertThrows(CartItemNotFoundException.class,
                () -> cartService.removeFromCart(userId, productId));
        }

        @Test
        @DisplayName("TC-CART-006: Update item in empty cart throws exception")
        void updateCartItem_emptyCart_throwsException() {
            // Arrange
            Cart emptyCart = new Cart(userId);
            when(valueOperations.get("cart:" + userId)).thenReturn(emptyCart);

            UpdateCartItemRequest request = new UpdateCartItemRequest(productId, 5);

            // Act & Assert
            assertThrows(CartItemNotFoundException.class,
                () -> cartService.updateCartItem(userId, request));
        }
    }

    // ============================================================
    // LARGE QUANTITY TESTS
    // ============================================================

    @Nested
    @DisplayName("Edge Case - Large Quantity")
    class LargeQuantityTests {

        @Test
        @DisplayName("TC-CART-007: Add quantity exceeding stock throws exception")
        void addToCart_exceedsStock_throwsException() {
            // Arrange
            Cart emptyCart = new Cart(userId);
            when(valueOperations.get("cart:" + userId)).thenReturn(emptyCart);

            Map<String, Object> product = Map.of(
                "id", productId.toString(),
                "name", "Limited Edition Item",
                "price", 199.99,
                "stock", 5,
                "imageUrl", ""
            );
            when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(product);

            AddToCartRequest request = new AddToCartRequest(productId, 10);

            // Act & Assert
            assertThrows(InsufficientStockException.class,
                () -> cartService.addToCart(userId, request));
        }

        @Test
        @DisplayName("TC-CART-008: Add maximum allowed quantity succeeds")
        void addToCart_maxQuantity_succeeds() {
            // Arrange
            Cart emptyCart = new Cart(userId);
            when(valueOperations.get("cart:" + userId)).thenReturn(emptyCart);

            Map<String, Object> product = Map.of(
                "id", productId.toString(),
                "name", "Bulk Item",
                "price", 9.99,
                "stock", 99,
                "imageUrl", ""
            );
            when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(product);

            AddToCartRequest request = new AddToCartRequest(productId, 99);

            // Act
            Cart result = cartService.addToCart(userId, request);

            // Assert
            assertEquals(99, result.getItems().get(0).getQuantity());
        }
    }

    // ============================================================
    // INVALID PRODUCT ID TESTS
    // ============================================================

    @Nested
    @DisplayName("Edge Case - Invalid Product ID")
    class InvalidProductTests {

        @Test
        @DisplayName("TC-CART-009: Add with null userId throws exception")
        void addToCart_nullUserId_throwsException() {
            AddToCartRequest request = new AddToCartRequest(productId, 1);

            assertThrows(IllegalArgumentException.class,
                () -> cartService.addToCart(null, request));
        }

        @Test
        @DisplayName("TC-CART-010: Add with null request throws exception")
        void addToCart_nullRequest_throwsException() {
            assertThrows(IllegalArgumentException.class,
                () -> cartService.addToCart(userId, null));
        }

        @Test
        @DisplayName("TC-CART-011: Product service returns null throws exception")
        void addToCart_productNotFound_throwsException() {
            // Arrange
            Cart emptyCart = new Cart(userId);
            when(valueOperations.get("cart:" + userId)).thenReturn(emptyCart);
            when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(null);

            AddToCartRequest request = new AddToCartRequest(productId, 1);

            // Act & Assert
            assertThrows(CartItemNotFoundException.class,
                () -> cartService.addToCart(userId, request));
        }
    }

    // ============================================================
    // COUPON TESTS
    // ============================================================

    @Nested
    @DisplayName("Edge Case - Invalid Coupon")
    class CouponTests {

        @Test
        @DisplayName("TC-CART-012: Apply valid coupon SAVE10 gives 10% discount")
        void applyCoupon_validCode_appliesDiscount() {
            // Arrange
            Cart cart = new Cart(userId);
            cart.getItems().add(new CartItem(productId, "Item", null,
                new BigDecimal("100.00"), 1));
            cart.recalculate();
            when(valueOperations.get("cart:" + userId)).thenReturn(cart);

            // Act
            Cart result = cartService.applyCoupon(userId, "SAVE10");

            // Assert
            assertNotNull(result.getCouponCode());
            assertTrue(result.getDiscount().compareTo(BigDecimal.ZERO) > 0);
        }

        @Test
        @DisplayName("TC-CART-013: Apply invalid coupon throws exception")
        void applyCoupon_invalidCode_throwsException() {
            // Arrange
            Cart cart = new Cart(userId);
            cart.getItems().add(new CartItem(productId, "Item", null,
                new BigDecimal("100.00"), 1));
            cart.recalculate();
            when(valueOperations.get("cart:" + userId)).thenReturn(cart);

            // Act & Assert
            assertThrows(IllegalArgumentException.class,
                () -> cartService.applyCoupon(userId, "INVALID123"));
        }

        @Test
        @DisplayName("TC-CART-014: Apply coupon with empty code throws exception")
        void applyCoupon_emptyCode_throwsException() {
            assertThrows(IllegalArgumentException.class,
                () -> cartService.applyCoupon(userId, ""));
        }
    }

    // ============================================================
    // CONCURRENT UPDATE SIMULATION
    // ============================================================

    @Nested
    @DisplayName("Edge Case - Concurrent Updates")
    class ConcurrentUpdateTests {

        @Test
        @DisplayName("TC-CART-015: Concurrent add and remove maintains consistency")
        void concurrentAddRemove_maintainsCartConsistency() {
            // Arrange
            UUID productA = UUID.randomUUID();
            UUID productB = UUID.randomUUID();

            Cart cart = new Cart(userId);
            cart.getItems().add(new CartItem(productA, "Product A", null,
                new BigDecimal("50.00"), 1));
            cart.getItems().add(new CartItem(productB, "Product B", null,
                new BigDecimal("30.00"), 2));
            cart.recalculate();
            when(valueOperations.get("cart:" + userId)).thenReturn(cart);

            // Act: Remove product A
            Cart result = cartService.removeFromCart(userId, productA);

            // Assert: Only product B remains
            assertEquals(1, result.getItems().size());
            assertEquals(productB, result.getItems().get(0).getProductId());
        }
    }

    // ============================================================
    // CLEAR CART TESTS
    // ============================================================

    @Nested
    @DisplayName("Clear Cart")
    class ClearCartTests {

        @Test
        @DisplayName("TC-CART-016: Clear cart removes all items")
        void clearCart_removesAll() {
            // Act
            cartService.clearCart(userId);

            // Assert
            verify(redisTemplate).delete("cart:" + userId);
        }

        @Test
        @DisplayName("TC-CART-017: Clear cart with null userId throws exception")
        void clearCart_nullUserId_throwsException() {
            assertThrows(IllegalArgumentException.class,
                () -> cartService.clearCart(null));
        }
    }
}
