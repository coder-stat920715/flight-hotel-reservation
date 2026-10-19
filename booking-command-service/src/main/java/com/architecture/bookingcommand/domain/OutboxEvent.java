package com.architecture.bookingcommand.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Transactional-outbox row. Column names follow the Debezium EventRouter defaults
 * (id, aggregatetype, aggregateid, type, payload). Debezium tails the WAL and publishes the row to
 * the Kafka topic named after {@code aggregatetype}, so the service never dual-writes to Kafka.
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    @Column(name = "id")
    private UUID id;
    @Column(name = "aggregatetype", nullable = false)
    private String aggregateType;
    @Column(name = "aggregateid", nullable = false)
    private String aggregateId;
    @Column(name = "type", nullable = false)
    private String type;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;
    @Column(name = "traceparent")
    private String traceparent;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected OutboxEvent() {
    }

    public OutboxEvent(String aggregateType, String aggregateId, String type, String payload, String traceparent) {
        this.id = UUID.randomUUID();
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.type = type;
        this.payload = payload;
        this.traceparent = traceparent;
    }

    public UUID getId() { return id; }
    public String getAggregateType() { return aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public String getType() { return type; }
    public String getPayload() { return payload; }
    public String getTraceparent() { return traceparent; }
    public Instant getCreatedAt() { return createdAt; }
}
