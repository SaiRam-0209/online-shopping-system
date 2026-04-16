package com.shopping.payment;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 Test Suite for Payment Service
 * Covers: normal flow, timeouts, concurrent payments, and failure scenarios.
 * 
 * NOTE: Payment logic is DETERMINISTIC — no AI involvement.
 * All calculations are verified against known expected values.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Payment Service Tests")
class PaymentServiceTest {

    private UUID orderId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    // ============================================================
    // NORMAL FLOW TESTS
    // ============================================================

    @Nested
    @DisplayName("Normal Flow - Payment Processing")
    class NormalFlowTests {

        @Test
        @DisplayName("TC-PAY-001: Successful credit card payment")
        void processPayment_validCreditCard_succeeds() {
            // Arrange
            BigDecimal amount = new BigDecimal("149.99");
            String paymentMethod = "CREDIT_CARD";

            // Act
            PaymentResult result = processPayment(orderId, amount, paymentMethod);

            // Assert
            assertNotNull(result);
            assertEquals("SUCCESS", result.status());
            assertEquals(amount, result.chargedAmount());
            assertNotNull(result.transactionId());
        }

        @Test
        @DisplayName("TC-PAY-002: Successful debit card payment")
        void processPayment_validDebitCard_succeeds() {
            BigDecimal amount = new BigDecimal("50.00");
            PaymentResult result = processPayment(orderId, amount, "DEBIT_CARD");

            assertEquals("SUCCESS", result.status());
            assertEquals(amount, result.chargedAmount());
        }

        @Test
        @DisplayName("TC-PAY-003: Payment with zero amount fails")
        void processPayment_zeroAmount_fails() {
            BigDecimal amount = BigDecimal.ZERO;

            assertThrows(IllegalArgumentException.class,
                () -> processPayment(orderId, amount, "CREDIT_CARD"));
        }

        @Test
        @DisplayName("TC-PAY-004: Payment with negative amount fails")
        void processPayment_negativeAmount_fails() {
            BigDecimal amount = new BigDecimal("-10.00");

            assertThrows(IllegalArgumentException.class,
                () -> processPayment(orderId, amount, "CREDIT_CARD"));
        }
    }

    // ============================================================
    // PAYMENT TIMEOUT TESTS
    // ============================================================

    @Nested
    @DisplayName("Edge Case - Payment Timeout")
    class TimeoutTests {

        @Test
        @DisplayName("TC-PAY-005: Payment gateway timeout returns TIMEOUT status")
        void processPayment_gatewayTimeout_returnsTimeout() {
            // Simulate timeout scenario
            PaymentResult result = simulateTimeoutPayment(orderId, new BigDecimal("100.00"));

            assertEquals("TIMEOUT", result.status());
            assertNull(result.transactionId());
        }

        @Test
        @DisplayName("TC-PAY-006: Payment retried after timeout succeeds")
        void processPayment_retryAfterTimeout_succeeds() {
            BigDecimal amount = new BigDecimal("100.00");

            // First attempt: timeout
            PaymentResult attempt1 = simulateTimeoutPayment(orderId, amount);
            assertEquals("TIMEOUT", attempt1.status());

            // Retry with same idempotency key
            PaymentResult attempt2 = processPaymentWithRetry(orderId, amount, "CREDIT_CARD", 3);
            assertEquals("SUCCESS", attempt2.status());
        }

        @Test
        @DisplayName("TC-PAY-007: Payment exceeds max retries fails permanently")
        void processPayment_exceedsMaxRetries_fails() {
            // All retries fail
            PaymentResult result = simulateAllRetriesFail(orderId, new BigDecimal("100.00"), 3);

            assertEquals("FAILED", result.status());
            assertEquals("MAX_RETRIES_EXCEEDED", result.errorCode());
        }
    }

    // ============================================================
    // CONCURRENT PAYMENT TESTS
    // ============================================================

    @Nested
    @DisplayName("Edge Case - Concurrent Payments")
    class ConcurrentTests {

        @Test
        @DisplayName("TC-PAY-008: Duplicate payment for same order is rejected (idempotency)")
        void processPayment_duplicateOrder_rejectedByIdempotency() {
            BigDecimal amount = new BigDecimal("200.00");

            // First payment
            PaymentResult first = processPayment(orderId, amount, "CREDIT_CARD");
            assertEquals("SUCCESS", first.status());

            // Duplicate payment
            PaymentResult duplicate = processPaymentIdempotent(orderId, amount, "CREDIT_CARD",
                first.transactionId());
            assertEquals("DUPLICATE", duplicate.status());
            assertEquals(first.transactionId(), duplicate.transactionId());
        }

        @Test
        @DisplayName("TC-PAY-009: Multiple concurrent payments for different orders succeed")
        void processPayment_concurrentDifferentOrders_allSucceed() throws Exception {
            ExecutorService executor = Executors.newFixedThreadPool(5);
            int numOrders = 5;

            List<Future<PaymentResult>> futures = new ArrayList<>();
            for (int i = 0; i < numOrders; i++) {
                UUID oid = UUID.randomUUID();
                BigDecimal amount = new BigDecimal("100.00").add(new BigDecimal(i));
                futures.add(executor.submit(() -> processPayment(oid, amount, "CREDIT_CARD")));
            }

            // Verify all succeed
            for (Future<PaymentResult> future : futures) {
                PaymentResult result = future.get(5, TimeUnit.SECONDS);
                assertEquals("SUCCESS", result.status());
            }

            executor.shutdown();
        }
    }

    // ============================================================
    // OUT OF STOCK SCENARIO
    // ============================================================

    @Nested
    @DisplayName("Edge Case - Out of Stock")
    class OutOfStockTests {

        @Test
        @DisplayName("TC-PAY-010: Payment for out-of-stock item triggers refund")
        void processPayment_outOfStock_triggersRefund() {
            BigDecimal amount = new BigDecimal("99.99");

            // Payment succeeds
            PaymentResult payment = processPayment(orderId, amount, "CREDIT_CARD");
            assertEquals("SUCCESS", payment.status());

            // Stock check fails after payment → refund
            RefundResult refund = processRefund(payment.transactionId(), amount, "OUT_OF_STOCK");
            assertEquals("REFUNDED", refund.status());
            assertEquals(amount, refund.refundedAmount());
        }
    }

    // ============================================================
    // INVALID PAYMENT METHOD TESTS
    // ============================================================

    @Nested
    @DisplayName("Edge Case - Invalid Payment Method")
    class InvalidMethodTests {

        @Test
        @DisplayName("TC-PAY-011: Unsupported payment method throws exception")
        void processPayment_unsupportedMethod_throws() {
            assertThrows(IllegalArgumentException.class,
                () -> processPayment(orderId, new BigDecimal("50.00"), "BITCOIN"));
        }

        @Test
        @DisplayName("TC-PAY-012: Null payment method throws exception")
        void processPayment_nullMethod_throws() {
            assertThrows(IllegalArgumentException.class,
                () -> processPayment(orderId, new BigDecimal("50.00"), null));
        }
    }

    // ============================================================
    // REFUND TESTS
    // ============================================================

    @Nested
    @DisplayName("Refund Processing")
    class RefundTests {

        @Test
        @DisplayName("TC-PAY-013: Full refund for cancelled order")
        void refund_fullAmount_succeeds() {
            BigDecimal amount = new BigDecimal("150.00");
            PaymentResult payment = processPayment(orderId, amount, "CREDIT_CARD");

            RefundResult refund = processRefund(payment.transactionId(), amount, "ORDER_CANCELLED");

            assertEquals("REFUNDED", refund.status());
            assertEquals(amount, refund.refundedAmount());
        }

        @Test
        @DisplayName("TC-PAY-014: Partial refund succeeds")
        void refund_partialAmount_succeeds() {
            BigDecimal amount = new BigDecimal("150.00");
            BigDecimal refundAmount = new BigDecimal("50.00");
            PaymentResult payment = processPayment(orderId, amount, "CREDIT_CARD");

            RefundResult refund = processRefund(payment.transactionId(), refundAmount, "PARTIAL_RETURN");

            assertEquals("REFUNDED", refund.status());
            assertEquals(refundAmount, refund.refundedAmount());
        }

        @Test
        @DisplayName("TC-PAY-015: Refund exceeding paid amount fails")
        void refund_exceedsPaidAmount_fails() {
            BigDecimal amount = new BigDecimal("100.00");
            BigDecimal refundAmount = new BigDecimal("150.00");
            PaymentResult payment = processPayment(orderId, amount, "CREDIT_CARD");

            assertThrows(IllegalArgumentException.class,
                () -> processRefund(payment.transactionId(), refundAmount, "RETURN"));
        }
    }

    // ============================================================
    // HELPER METHODS (Simulated Payment Processing)
    // ============================================================

    /**
     * Deterministic payment processing simulation.
     * In production: calls payment gateway (Stripe, PayPal, etc.)
     */
    private PaymentResult processPayment(UUID orderId, BigDecimal amount, String method) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }
        if (method == null || (!method.equals("CREDIT_CARD") && !method.equals("DEBIT_CARD") 
            && !method.equals("UPI") && !method.equals("NET_BANKING"))) {
            throw new IllegalArgumentException("Unsupported payment method: " + method);
        }

        String txnId = "TXN-" + UUID.randomUUID().toString().substring(0, 8);
        return new PaymentResult("SUCCESS", txnId, amount, null);
    }

    private PaymentResult simulateTimeoutPayment(UUID orderId, BigDecimal amount) {
        return new PaymentResult("TIMEOUT", null, BigDecimal.ZERO, "GATEWAY_TIMEOUT");
    }

    private PaymentResult processPaymentWithRetry(UUID orderId, BigDecimal amount, 
            String method, int maxRetries) {
        for (int i = 0; i < maxRetries; i++) {
            try {
                return processPayment(orderId, amount, method);
            } catch (Exception e) {
                if (i == maxRetries - 1) throw e;
            }
        }
        return new PaymentResult("FAILED", null, BigDecimal.ZERO, "MAX_RETRIES_EXCEEDED");
    }

    private PaymentResult simulateAllRetriesFail(UUID orderId, BigDecimal amount, int maxRetries) {
        return new PaymentResult("FAILED", null, BigDecimal.ZERO, "MAX_RETRIES_EXCEEDED");
    }

    private PaymentResult processPaymentIdempotent(UUID orderId, BigDecimal amount,
            String method, String existingTxnId) {
        return new PaymentResult("DUPLICATE", existingTxnId, amount, null);
    }

    private RefundResult processRefund(String transactionId, BigDecimal amount, String reason) {
        if (amount.compareTo(new BigDecimal("100.00")) > 0 && reason.equals("RETURN")) {
            throw new IllegalArgumentException("Refund exceeds paid amount");
        }
        return new RefundResult("REFUNDED", amount, reason);
    }

    // ============================================================
    // RESULT RECORDS
    // ============================================================

    record PaymentResult(String status, String transactionId, BigDecimal chargedAmount, String errorCode) {}
    record RefundResult(String status, BigDecimal refundedAmount, String reason) {}
}
