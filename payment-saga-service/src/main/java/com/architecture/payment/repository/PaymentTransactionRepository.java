package com.architecture.payment.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.architecture.payment.domain.PaymentTransaction;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {
    boolean existsByBookingIdAndTxnType(UUID bookingId, String txnType);

    boolean existsByBookingIdAndTxnTypeAndStatus(UUID bookingId, String txnType, String status);
}
