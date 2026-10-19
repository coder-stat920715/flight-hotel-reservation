package com.architecture.payment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "payment_transactions")
public class PaymentTransaction {

    @Id
    @Column(name = "txn_id")
    private UUID txnId = UUID.randomUUID();
    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;
    @Column(name = "txn_type", nullable = false)
    private String txnType;
    @Column(name = "amount", nullable = false)
    private BigDecimal amount;
    @Column(name = "status", nullable = false)
    private String status;
    @Column(name = "failure_reason")
    private String failureReason;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected PaymentTransaction() {
    }

    public PaymentTransaction(UUID bookingId, String txnType, BigDecimal amount, String status, String failureReason) {
        this.bookingId = bookingId;
        this.txnType = txnType;
        this.amount = amount;
        this.status = status;
        this.failureReason = failureReason;
    }

    public UUID getTxnId() { return txnId; }
    public UUID getBookingId() { return bookingId; }
    public String getTxnType() { return txnType; }
    public BigDecimal getAmount() { return amount; }
    public String getStatus() { return status; }
}
