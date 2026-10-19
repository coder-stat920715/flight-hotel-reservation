package com.architecture.flighthotelquery.consumer;

import java.math.BigDecimal;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Projects booking events (published by Debezium from the command side's outbox) into the
 * read model table. Idempotent upsert, so redelivery is harmless.
 */
@Component
public class KafkaBookingEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaBookingEventConsumer.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public KafkaBookingEventConsumer(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @KafkaListener(topics = "booking", groupId = "flight-hotel-query")
    public void onBookingEvent(String message) throws Exception {
        JsonNode n = mapper.readTree(message);
        MDC.put("userId", n.path("userId").asText("system"));
        MDC.put("tenantId", n.path("tenantId").asText("unknown"));
        try {
            jdbc.update("""
                INSERT INTO booking_read_model
                    (booking_id, user_id, tenant_id, source_code, dest_code, status, total_amount, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, now())
                ON CONFLICT (booking_id) DO UPDATE
                   SET status = EXCLUDED.status, total_amount = EXCLUDED.total_amount, updated_at = now()
                """,
                UUID.fromString(n.path("bookingId").asText()),
                n.path("userId").asText(), n.path("tenantId").asText(),
                n.path("sourceCode").asText(), n.path("destCode").asText(),
                n.path("status").asText(), new BigDecimal(n.path("totalAmount").asText("0")));
            log.info("Read model updated for booking {} ({})", n.path("bookingId").asText(), n.path("eventType").asText());
        } finally {
            MDC.remove("userId");
            MDC.remove("tenantId");
        }
    }
}
