package com.architecture.payment.consumer;

import java.math.BigDecimal;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import com.architecture.payment.service.PaymentSagaService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Consumes saga commands from "booking-payment".
 * attempts = 4  -> 1 initial delivery + 3 retries with exponential backoff (1 s, 2 s, 4 s)
 * Retry topics: booking-payment-retry-*, dead-letter topic: booking-payment.DLT
 * Resilience4j (circuit breaker + retry + fallback) protects the outbound gateway call inside the service.
 */
@Component
public class PaymentSagaConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentSagaConsumer.class);

    private final PaymentSagaService sagaService;
    private final ObjectMapper mapper;

    public PaymentSagaConsumer(PaymentSagaService sagaService, ObjectMapper mapper) {
        this.sagaService = sagaService;
        this.mapper = mapper;
    }

    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        retryTopicSuffix = "-retry",
        dltTopicSuffix = ".DLT",
        exclude = {IllegalArgumentException.class},     // poison messages go straight to the DLT
        autoCreateTopics = "true",
        numPartitions = "3",
        replicationFactor = "1")
    @KafkaListener(topics = "booking-payment", groupId = "payment-saga")
    public void onPaymentCommand(String message) {
        JsonNode event = parse(message);
        MDC.put("userId", event.path("userId").asText("system"));
        MDC.put("tenantId", event.path("tenantId").asText("unknown"));
        try {
            sagaService.process(event);
        } finally {
            MDC.remove("userId");
            MDC.remove("tenantId");
        }
    }

    @DltHandler
    public void onDeadLetter(String message, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("Message routed to {} after retries exhausted: {}", topic, message);
        try {
            JsonNode event = mapper.readTree(message);
            if ("PAYMENT_REQUESTED".equals(event.path("eventType").asText())) {
                sagaService.failAfterRetriesExhausted(
                    UUID.fromString(event.path("bookingId").asText()),
                    new BigDecimal(event.path("totalAmount").asText("0")), "DLT_RETRIES_EXHAUSTED");
            }
        } catch (Exception e) {
            log.error("Unable to compensate from DLT message", e);
        }
    }

    private JsonNode parse(String message) {
        try {
            return mapper.readTree(message);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Malformed payload", e);
        }
    }
}
