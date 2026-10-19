package com.architecture.bookingcommand.consumer;

import java.util.UUID;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.architecture.bookingcommand.service.BookingCommandService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Receives PAYMENT_COMPLETED / PAYMENT_FAILED replies from the payment saga (via Debezium). */
@Component
public class BookingSagaReplyConsumer {

    private final BookingCommandService service;
    private final ObjectMapper mapper;

    public BookingSagaReplyConsumer(BookingCommandService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @KafkaListener(topics = "booking-saga", groupId = "booking-command")
    public void onSagaReply(String message) throws Exception {
        JsonNode n = mapper.readTree(message);
        service.applySagaResult(UUID.fromString(n.path("bookingId").asText()), n.path("eventType").asText());
    }
}
