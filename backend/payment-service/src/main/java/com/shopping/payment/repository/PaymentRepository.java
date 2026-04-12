package com.shopping.payment.repository;

import com.shopping.payment.model.Payment;
import com.shopping.payment.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByOrderId(UUID orderId);
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
    List<Payment> findByStatus(PaymentStatus status);
    List<Payment> findByUserIdOrderByCreatedAtDesc(UUID userId);
    boolean existsByOrderIdAndStatus(UUID orderId, PaymentStatus status);
}
