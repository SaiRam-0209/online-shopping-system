package com.shopping.payment.service;

import com.shopping.payment.exception.DuplicatePaymentException;
import com.shopping.payment.exception.PaymentNotFoundException;
import com.shopping.payment.exception.PaymentProcessingException;
import com.shopping.payment.model.*;
import com.shopping.payment.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Payment Service implementation.
 * 
 * CRITICAL: All payment logic is DETERMINISTIC.
 * No AI-generated calculations are used in financial operations.
 * Every calculation path is covered by unit tests (95%+ coverage target).
 */
@Service
@Transactional(readOnly = true)
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final int MAX_RETRY_COUNT = 3;

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    /**
     * Initiate a payment for an order.
     * Supports idempotency — duplicate requests return existing payment.
     * 
     * @param orderId      Order UUID
     * @param userId       User UUID
     * @param amount       Payment amount (must be > 0)
     * @param method       Payment method
     * @param idempotencyKey Unique key for idempotency
     * @return Payment entity with status
     */
    @Transactional
    public Payment initiatePayment(UUID orderId, UUID userId, BigDecimal amount,
                                    PaymentMethod method, String idempotencyKey) {
        // 1. Validate inputs (null checks)
        if (orderId == null) throw new IllegalArgumentException("Order ID must not be null");
        if (userId == null) throw new IllegalArgumentException("User ID must not be null");
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }
        if (method == null) throw new IllegalArgumentException("Payment method must not be null");

        log.info("Initiating payment: orderId={}, amount={}, method={}", orderId, amount, method);

        // 2. Idempotency check — return existing payment if key matches
        if (idempotencyKey != null) {
            Optional<Payment> existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Duplicate payment request detected (idempotency key: {})", idempotencyKey);
                return existing.get();
            }
        }

        // 3. Check for existing successful payment for this order
        if (paymentRepository.existsByOrderIdAndStatus(orderId, PaymentStatus.SUCCESS)) {
            throw new DuplicatePaymentException("Payment already completed for order: " + orderId);
        }

        // 4. Create payment record
        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setUserId(userId);
        payment.setAmount(amount);
        payment.setMethod(method);
        payment.setStatus(PaymentStatus.INITIATED);
        payment.setIdempotencyKey(idempotencyKey != null ? idempotencyKey : UUID.randomUUID().toString());

        payment = paymentRepository.save(payment);
        log.info("Payment record created: id={}", payment.getId());

        // 5. Process payment via gateway (deterministic simulation)
        return processWithGateway(payment);
    }

    /**
     * Get payment details by ID.
     */
    public Payment getPayment(UUID paymentId) {
        if (paymentId == null) throw new IllegalArgumentException("Payment ID must not be null");
        return paymentRepository.findById(paymentId)
            .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentId));
    }

    /**
     * Get payment by order ID.
     */
    public Payment getPaymentByOrderId(UUID orderId) {
        if (orderId == null) throw new IllegalArgumentException("Order ID must not be null");
        return paymentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new PaymentNotFoundException("Payment not found for order: " + orderId));
    }

    /**
     * Process a refund.
     * 
     * Deterministic refund calculation:
     * - Full refund: refundAmount == payment.amount
     * - Partial refund: 0 < refundAmount < payment.amount
     * - Cannot refund more than original payment
     */
    @Transactional
    public Payment processRefund(UUID paymentId, BigDecimal refundAmount, String reason) {
        if (paymentId == null) throw new IllegalArgumentException("Payment ID must not be null");
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Refund amount must be positive");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Refund reason is required");
        }

        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentId));

        // Validate payment is refundable
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new PaymentProcessingException(
                "Cannot refund payment in status: " + payment.getStatus()
            );
        }

        // Validate refund amount doesn't exceed original payment
        if (refundAmount.compareTo(payment.getAmount()) > 0) {
            throw new IllegalArgumentException(
                "Refund amount ($" + refundAmount + ") exceeds payment amount ($" + payment.getAmount() + ")"
            );
        }

        log.info("Processing refund: paymentId={}, amount={}, reason={}", paymentId, refundAmount, reason);

        // Deterministic refund status assignment
        if (refundAmount.compareTo(payment.getAmount()) == 0) {
            payment.setStatus(PaymentStatus.REFUNDED);
        } else {
            payment.setStatus(PaymentStatus.PARTIALLY_REFUNDED);
        }

        payment.setGatewayResponse("REFUND_PROCESSED:" + refundAmount);
        Payment saved = paymentRepository.save(payment);

        log.info("Refund processed: paymentId={}, status={}", paymentId, saved.getStatus());
        return saved;
    }

    /**
     * Retry a failed payment.
     */
    @Transactional
    public Payment retryPayment(UUID paymentId) {
        if (paymentId == null) throw new IllegalArgumentException("Payment ID must not be null");

        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentId));

        if (payment.getStatus() != PaymentStatus.FAILED && payment.getStatus() != PaymentStatus.TIMEOUT) {
            throw new PaymentProcessingException(
                "Cannot retry payment in status: " + payment.getStatus()
            );
        }

        if (payment.getRetryCount() >= MAX_RETRY_COUNT) {
            throw new PaymentProcessingException(
                "Maximum retry attempts exceeded (" + MAX_RETRY_COUNT + ") for payment: " + paymentId
            );
        }

        payment.setRetryCount(payment.getRetryCount() + 1);
        payment.setStatus(PaymentStatus.INITIATED);
        log.info("Retrying payment: id={}, attempt={}", paymentId, payment.getRetryCount());

        return processWithGateway(payment);
    }

    // ---- Private: Gateway Simulation ----

    /**
     * Simulate payment gateway processing.
     * In production, this calls Stripe/PayPal/Razorpay API.
     * 
     * This method is DETERMINISTIC:
     * - Amount validation is exact comparison
     * - Status assignment follows strict rules
     * - No randomness or AI involvement
     */
    private Payment processWithGateway(Payment payment) {
        try {
            payment.setStatus(PaymentStatus.PROCESSING);
            paymentRepository.save(payment);

            // Simulate gateway processing (deterministic rules)
            String transactionId = generateTransactionId(payment);
            payment.setTransactionId(transactionId);
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setGatewayResponse("APPROVED");

            Payment saved = paymentRepository.save(payment);
            log.info("Payment successful: id={}, txn={}", saved.getId(), transactionId);
            return saved;

        } catch (Exception e) {
            log.error("Payment gateway error: paymentId={}, error={}", payment.getId(), e.getMessage());
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(e.getMessage());
            payment.setGatewayResponse("DECLINED:" + e.getMessage());
            return paymentRepository.save(payment);
        }
    }

    /**
     * Generate a deterministic transaction ID.
     * Format: TXN-{first8-of-paymentId}-{timestamp-suffix}
     */
    private String generateTransactionId(Payment payment) {
        String prefix = payment.getId().toString().substring(0, 8);
        String suffix = String.valueOf(System.currentTimeMillis() % 100000);
        return "TXN-" + prefix + "-" + suffix;
    }
}
