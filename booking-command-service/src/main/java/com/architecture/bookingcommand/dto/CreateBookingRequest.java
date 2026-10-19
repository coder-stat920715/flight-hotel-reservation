package com.architecture.bookingcommand.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/** CREATE_BOOKING command. */
public record CreateBookingRequest(
        @NotBlank String tenantId,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String sourceCode,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String destCode,
        @NotNull Long flightId,
        Long hotelId,
        @Min(1) @Max(60) int stayNights,
        @NotNull @Positive BigDecimal totalAmount) {
}
