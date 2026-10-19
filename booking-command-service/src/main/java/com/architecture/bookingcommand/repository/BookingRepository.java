package com.architecture.bookingcommand.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.architecture.bookingcommand.domain.Booking;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
}
