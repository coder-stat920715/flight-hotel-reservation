package com.architecture.bookingcommand.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.architecture.bookingcommand.domain.Booking;
import com.architecture.bookingcommand.domain.OutboxEvent;
import com.architecture.bookingcommand.dto.BookingResponse;
import com.architecture.bookingcommand.dto.CreateBookingRequest;
import com.architecture.bookingcommand.exception.BookingNotFoundException;
import com.architecture.bookingcommand.repository.BookingRepository;
import com.architecture.bookingcommand.repository.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;

/**
 * Write side of CQRS. Every state change and its outbox rows commit in ONE database transaction
 * (Transactional Outbox), so there is no dual-write between PostgreSQL and Kafka.
 * Topics (== aggregatetype): "booking" (read-side sync), "booking-payment" (saga commands).
 */
@Service
public class BookingCommandService {

    private static final Logger log = LoggerFactory.getLogger(BookingCommandService.class);
    static final String TOPIC_BOOKING = "booking";
    static final String TOPIC_PAYMENT = "booking-payment";

    private final BookingRepository bookings;
    private final OutboxRepository outbox;
    private final ObjectMapper mapper;
    private final ObjectProvider<Tracer> tracer;

    public BookingCommandService(BookingRepository bookings, OutboxRepository outbox, ObjectMapper mapper,
                                 ObjectProvider<Tracer> tracer) {
        this.bookings = bookings;
        this.outbox = outbox;
        this.mapper = mapper;
        this.tracer = tracer;
    }

    @Transactional
    public BookingResponse createBooking(CreateBookingRequest r, String userId) {
        Booking b = new Booking();
        b.setBookingId(UUID.randomUUID());
        b.setUserId(userId);
        b.setTenantId(r.tenantId());
        b.setSourceCode(r.sourceCode().toUpperCase());
        b.setDestCode(r.destCode().toUpperCase());
        b.setFlightId(r.flightId());
        b.setHotelId(r.hotelId());
        b.setStayNights(r.stayNights());
        b.setTotalAmount(r.totalAmount());
        b.setStatus("PENDING_PAYMENT");
        bookings.save(b);
        publish(b, TOPIC_BOOKING, "BOOKING_CREATED");
        publish(b, TOPIC_PAYMENT, "PAYMENT_REQUESTED");
        log.info("Booking {} created", b.getBookingId());
        return BookingResponse.from(b);
    }

    @Transactional
    public BookingResponse cancelBooking(UUID bookingId, String userId, boolean admin) {
        Booking b = bookings.findById(bookingId).orElseThrow(() -> new BookingNotFoundException(bookingId));
        if (!admin && !b.getUserId().equals(userId)) {
            throw new AccessDeniedException("Caller does not own this booking");
        }
        if ("CANCELLED".equals(b.getStatus())) {
            return BookingResponse.from(b);
        }
        boolean wasPaid = "CONFIRMED".equals(b.getStatus());
        b.setStatus("CANCELLED");
        publish(b, TOPIC_BOOKING, "BOOKING_CANCELLED");
        if (wasPaid) {
            publish(b, TOPIC_PAYMENT, "REFUND_REQUESTED");
        }
        return BookingResponse.from(b);
    }

    /** Saga reply handler (events published by payment-saga-service on topic "booking-saga"). */
    @Transactional
    public void applySagaResult(UUID bookingId, String eventType) {
        Booking b = bookings.findById(bookingId).orElse(null);
        if (b == null) {
            log.warn("Saga reply for unknown booking {}", bookingId);
            return;
        }
        switch (eventType) {
            case "PAYMENT_COMPLETED" -> {
                if ("CANCELLED".equals(b.getStatus())) {
                    // customer cancelled while payment was in flight -> compensate
                    publish(b, TOPIC_PAYMENT, "REFUND_REQUESTED");
                } else if (!"CONFIRMED".equals(b.getStatus())) {
                    b.setStatus("CONFIRMED");
                    publish(b, TOPIC_BOOKING, "BOOKING_CONFIRMED");
                }
            }
            case "PAYMENT_FAILED" -> {
                if ("PENDING_PAYMENT".equals(b.getStatus())) {
                    b.setStatus("PAYMENT_FAILED");
                    publish(b, TOPIC_BOOKING, "BOOKING_PAYMENT_FAILED");
                }
            }
            default -> log.debug("Ignoring saga event {}", eventType);
        }
    }

    private void publish(Booking b, String aggregateType, String type) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("eventType", type);
        p.put("bookingId", b.getBookingId().toString());
        p.put("userId", b.getUserId());
        p.put("tenantId", b.getTenantId());
        p.put("sourceCode", b.getSourceCode());
        p.put("destCode", b.getDestCode());
        p.put("status", b.getStatus());
        p.put("totalAmount", b.getTotalAmount());
        p.put("stayNights", b.getStayNights());
        p.put("occurredAt", java.time.Instant.now().toString());
        try {
            outbox.save(new OutboxEvent(aggregateType, b.getBookingId().toString(), type,
                mapper.writeValueAsString(p), traceparent()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise outbox payload", e);
        }
    }

    /** W3C traceparent persisted with the outbox row; Debezium forwards it as a Kafka header. */
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
