package com.architecture.payment.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.architecture.payment.domain.PaymentTransaction;
import com.architecture.payment.dto.PaymentResult;
import com.architecture.payment.exception.PaymentUnavailableException;
import com.architecture.payment.repository.PaymentTransactionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;

/**
 * Saga participant/orchestrator for payments. Local DB changes and the reply event (outbox row on
 * topic "booking-saga") commit in one transaction. The external gateway call is made OUTSIDE the transaction.
 */
@Service
public class PaymentSagaService {

    private static final Logger log = LoggerFactory.getLogger(PaymentSagaService.class);

    private final PaymentTransactionRepository txRepo;
    private final PaymentGatewayClient gateway;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;
    private final ObjectProvider<Tracer> tracer;

    public PaymentSagaService(PaymentTransactionRepository txRepo, PaymentGatewayClient gateway, JdbcTemplate jdbc,
                              ObjectMapper mapper, TransactionTemplate tx, ObjectProvider<Tracer> tracer) {
        this.txRepo = txRepo;
        this.gateway = gateway;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.tx = tx;
        this.tracer = tracer;
    }

    public void process(JsonNode event) {
        String type = event.path("eventType").asText();
        UUID bookingId = UUID.fromString(event.path("bookingId").asText());   // IllegalArgumentException -> DLT
        BigDecimal amount = new BigDecimal(event.path("totalAmount").asText("0"));
        switch (type) {
            case "PAYMENT_REQUESTED" -> charge(bookingId, amount);
            case "REFUND_REQUESTED" -> refund(bookingId, amount);
            default -> log.debug("Ignoring event type {}", type);
        }
    }

    private void charge(UUID bookingId, BigDecimal amount) {
        if (txRepo.existsByBookingIdAndTxnType(bookingId, "CHARGE")) {
            log.info("Charge for {} already processed (idempotent skip)", bookingId);
            return;
        }
        PaymentResult result = gateway.charge(bookingId, amount);
        if (result.deferred()) {
            throw new PaymentUnavailableException("Payment gateway unavailable: " + result.reason());
        }
        tx.executeWithoutResult(s -> {
            txRepo.save(new PaymentTransaction(bookingId, "CHARGE", amount,
                result.approved() ? "SUCCESS" : "DECLINED", result.reason()));
            emit(bookingId, result.approved() ? "PAYMENT_COMPLETED" : "PAYMENT_FAILED", result.reason());
        });
    }

    private void refund(UUID bookingId, BigDecimal amount) {
        if (!txRepo.existsByBookingIdAndTxnTypeAndStatus(bookingId, "CHARGE", "SUCCESS")
            || txRepo.existsByBookingIdAndTxnType(bookingId, "REFUND")) {
            log.info("No refund needed for {}", bookingId);
            return;
        }
        tx.executeWithoutResult(s -> {
            txRepo.save(new PaymentTransaction(bookingId, "REFUND", amount, "SUCCESS", null));
            emit(bookingId, "PAYMENT_REFUNDED", null);
        });
    }

    /** Compensation when all Kafka retries are exhausted (called from the DLT handler). */
    public void failAfterRetriesExhausted(UUID bookingId, BigDecimal amount, String reason) {
        if (txRepo.existsByBookingIdAndTxnType(bookingId, "CHARGE")) {
            return;
        }
        tx.executeWithoutResult(s -> {
            txRepo.save(new PaymentTransaction(bookingId, "CHARGE", amount, "FAILED", reason));
            emit(bookingId, "PAYMENT_FAILED", reason);
        });
    }

    private void emit(UUID bookingId, String eventType, String reason) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("eventType", eventType);
        p.put("bookingId", bookingId.toString());
        p.put("reason", reason);
        p.put("occurredAt", Instant.now().toString());
        try {
            jdbc.update("INSERT INTO outbox_events (id, aggregatetype, aggregateid, type, payload, traceparent) "
                    + "VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?)",
                UUID.randomUUID(), "booking-saga", bookingId.toString(), eventType,
                mapper.writeValueAsString(p), traceparent());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private String traceparent() {
        Tracer t = tracer.getIfAvailable();
        Span span = t == null ? null : t.currentSpan();
        if (span == null) {
            return null;
        }
        return "00-" + span.context().traceId() + "-" + span.context().spanId()
            + (Boolean.TRUE.equals(span.context().sampled()) ? "-01" : "-00");
    }
}
