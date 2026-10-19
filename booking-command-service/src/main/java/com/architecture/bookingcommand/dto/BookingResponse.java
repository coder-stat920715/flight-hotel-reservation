package com.architecture.bookingcommand.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.architecture.bookingcommand.domain.Booking;

public record BookingResponse(UUID bookingId, String status, String sourceCode, String destCode,
                              BigDecimal totalAmount, Instant createdAt) {

    public static BookingResponse from(Booking b) {
        return new BookingResponse(b.getBookingId(), b.getStatus(), b.getSourceCode(), b.getDestCode(),
            b.getTotalAmount(), b.getCreatedAt());
    }
}
