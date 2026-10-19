package com.architecture.bookingcommand.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.architecture.bookingcommand.domain.OutboxEvent;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
}
