package com.shopping.payment.controller;

import com.shopping.payment.model.Payment;
import com.shopping.payment.model.PaymentMethod;
import com.shopping.payment.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for Payment Service.
 * All payment endpoints enforce strict validation.
 */
@RestController
@RequestMapping("/api/v1/payments")
@Validated
@CrossOrigin(origins = "*", maxAge = 3600)
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * POST /api/v1/payments
     * Initiate a new payment.
     */
    @PostMapping
    public ResponseEntity<Payment> initiatePayment(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        UUID orderId = UUID.fromString(request.get("orderId").toString());
        UUID userId = UUID.fromString(request.getOrDefault("userId", UUID.randomUUID()).toString());
        BigDecimal amount = new BigDecimal(request.get("amount").toString());
        PaymentMethod method = PaymentMethod.valueOf(
            request.get("method").toString().toUpperCase());

        log.info("POST /api/v1/payments - orderId={}, amount={}", orderId, amount);

        Payment payment = paymentService.initiatePayment(orderId, userId, amount, method, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(payment);
    }

    /**
     * GET /api/v1/payments/{id}
     * Get payment status.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Payment> getPayment(@PathVariable UUID id) {
        log.info("GET /api/v1/payments/{}", id);
        return ResponseEntity.ok(paymentService.getPayment(id));
    }

    /**
     * POST /api/v1/payments/{id}/refund
     * Process a refund.
     */
    @PostMapping("/{id}/refund")
    public ResponseEntity<Payment> processRefund(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> request) {

        BigDecimal amount = new BigDecimal(request.get("amount").toString());
        String reason = (String) request.get("reason");

        log.info("POST /api/v1/payments/{}/refund - amount={}", id, amount);

        Payment refunded = paymentService.processRefund(id, amount, reason);
        return ResponseEntity.ok(refunded);
    }

    /**
     * POST /api/v1/payments/{id}/retry
     * Retry a failed payment.
     */
    @PostMapping("/{id}/retry")
    public ResponseEntity<Payment> retryPayment(@PathVariable UUID id) {
        log.info("POST /api/v1/payments/{}/retry", id);
        return ResponseEntity.ok(paymentService.retryPayment(id));
    }

    /**
     * GET /api/v1/payments/methods
     * List available payment methods.
     */
    @GetMapping("/methods")
    public ResponseEntity<PaymentMethod[]> getPaymentMethods() {
        return ResponseEntity.ok(PaymentMethod.values());
    }
}
