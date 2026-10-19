-- V1__init_travel_schema.sql  (PostgreSQL 14+)
-- Geography ---------------------------------------------------------------------------
CREATE TABLE countries (
    country_id    BIGSERIAL PRIMARY KEY,
    country_code  CHAR(2)      NOT NULL UNIQUE,
    country_name  VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE cities (
    city_id     BIGSERIAL PRIMARY KEY,
    country_id  BIGINT       NOT NULL REFERENCES countries (country_id),
    city_name   VARCHAR(100) NOT NULL,
    UNIQUE (country_id, city_name)
);

CREATE TABLE airports (
    airport_id    BIGSERIAL PRIMARY KEY,
    airport_code  VARCHAR(3)   NOT NULL UNIQUE,
    airport_name  VARCHAR(150) NOT NULL,
    city_id       BIGINT       NOT NULL REFERENCES cities (city_id)
);

-- Flight system -----------------------------------------------------------------------
CREATE TABLE airlines (
    airline_id    BIGSERIAL PRIMARY KEY,
    airline_code  VARCHAR(3)   NOT NULL UNIQUE,
    airline_name  VARCHAR(100) NOT NULL
);

-- LIST partitioning by flight_type: domestic searches never scan international rows (and vice versa).
CREATE TABLE flights (
    flight_id              BIGSERIAL,
    flight_number          VARCHAR(10) NOT NULL,
    airline_id             BIGINT      NOT NULL REFERENCES airlines (airline_id),
    source_airport_id      BIGINT      NOT NULL REFERENCES airports (airport_id),
    destination_airport_id BIGINT      NOT NULL REFERENCES airports (airport_id),
    flight_type            VARCHAR(13) NOT NULL CHECK (flight_type IN ('DOMESTIC', 'INTERNATIONAL')),
    departure_time         TIME        NOT NULL,
    duration_minutes       INT         NOT NULL,
    PRIMARY KEY (flight_id, flight_type),
    UNIQUE (flight_number, flight_type)
) PARTITION BY LIST (flight_type);

CREATE TABLE flights_domestic      PARTITION OF flights FOR VALUES IN ('DOMESTIC');
CREATE TABLE flights_international PARTITION OF flights FOR VALUES IN ('INTERNATIONAL');

-- RANGE partitioning by departure_date (quarterly): date-bounded searches prune to one partition.
-- (flight_id is not an enforced FK: PostgreSQL requires the full partition key to reference a partitioned table.)
CREATE TABLE flight_fares (
    fare_id          BIGSERIAL,
    flight_id        BIGINT        NOT NULL,
    fare_class       VARCHAR(20)   NOT NULL,
    base_fare        NUMERIC(12,2) NOT NULL,
    taxes            NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_fare       NUMERIC(12,2) NOT NULL,
    currency         CHAR(3)       NOT NULL DEFAULT 'INR',
    departure_date   DATE          NOT NULL,
    seats_available  INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (fare_id, departure_date)
) PARTITION BY RANGE (departure_date);

CREATE TABLE flight_fares_2026_q4 PARTITION OF flight_fares FOR VALUES FROM ('2026-10-01') TO ('2027-01-01');
CREATE TABLE flight_fares_2027_q1 PARTITION OF flight_fares FOR VALUES FROM ('2027-01-01') TO ('2027-04-01');
CREATE TABLE flight_fares_2027_q2 PARTITION OF flight_fares FOR VALUES FROM ('2027-04-01') TO ('2027-07-01');
CREATE TABLE flight_fares_default PARTITION OF flight_fares DEFAULT;

-- Run from a scheduler (pg_cron / Kubernetes CronJob) ahead of each quarter.
CREATE OR REPLACE FUNCTION create_fare_partition(p_start DATE, p_end DATE) RETURNS void AS $$
BEGIN
    EXECUTE format('CREATE TABLE IF NOT EXISTS flight_fares_%s PARTITION OF flight_fares FOR VALUES FROM (%L) TO (%L)',
                   to_char(p_start, 'YYYY_MM'), p_start, p_end);
END;
$$ LANGUAGE plpgsql;

-- Hotel system ------------------------------------------------------------------------
CREATE TABLE hotels (
    hotel_id     BIGSERIAL PRIMARY KEY,
    hotel_name   VARCHAR(150) NOT NULL,
    city_id      BIGINT       NOT NULL REFERENCES cities (city_id),
    star_rating  SMALLINT     NOT NULL CHECK (star_rating BETWEEN 1 AND 5),
    address      VARCHAR(255)
);

-- city_id is denormalised here so that (city_id, price_per_night) can be one composite index.
CREATE TABLE hotel_pricing (
    pricing_id      BIGSERIAL PRIMARY KEY,
    hotel_id        BIGINT        NOT NULL REFERENCES hotels (hotel_id),
    city_id         BIGINT        NOT NULL REFERENCES cities (city_id),
    room_type       VARCHAR(50)   NOT NULL,
    price_per_night NUMERIC(12,2) NOT NULL,
    currency        CHAR(3)       NOT NULL DEFAULT 'INR',
    is_available    BOOLEAN       NOT NULL DEFAULT TRUE
);

-- Sightseeing -------------------------------------------------------------------------
CREATE TABLE attractions (
    attraction_id         BIGSERIAL PRIMARY KEY,
    city_id               BIGINT        NOT NULL REFERENCES cities (city_id),
    attraction_name       VARCHAR(150)  NOT NULL,
    entry_fee_per_person  NUMERIC(10,2) NOT NULL DEFAULT 0
);

-- Transactional outbox (Debezium EventRouter column names) ----------------------------
CREATE TABLE outbox_events (
    id            UUID PRIMARY KEY,
    aggregatetype VARCHAR(100) NOT NULL,   -- == Kafka topic: booking | booking-payment | booking-saga
    aggregateid   VARCHAR(100) NOT NULL,   -- Kafka message key (per-booking ordering)
    type          VARCHAR(100) NOT NULL,
    payload       JSONB        NOT NULL,
    traceparent   VARCHAR(64),             -- W3C trace context forwarded as Kafka header
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Booking & Saga ----------------------------------------------------------------------
CREATE TABLE bookings (
    booking_id    UUID PRIMARY KEY,
    user_id       VARCHAR(64)   NOT NULL,
    tenant_id     VARCHAR(64)   NOT NULL,
    source_code   VARCHAR(3)    NOT NULL,
    dest_code     VARCHAR(3)    NOT NULL,
    flight_id     BIGINT        NOT NULL,
    hotel_id      BIGINT,
    stay_nights   INT           NOT NULL,
    total_amount  NUMERIC(14,2) NOT NULL,
    status        VARCHAR(30)   NOT NULL,
    version       BIGINT        NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE payment_transactions (
    txn_id          UUID PRIMARY KEY,
    booking_id      UUID          NOT NULL REFERENCES bookings (booking_id),
    txn_type        VARCHAR(20)   NOT NULL,      -- CHARGE | REFUND
    amount          NUMERIC(14,2) NOT NULL,
    status          VARCHAR(20)   NOT NULL,      -- SUCCESS | DECLINED | FAILED
    failure_reason  VARCHAR(255),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- CQRS read model, populated from Kafka by flight-hotel-query-service
CREATE TABLE booking_read_model (
    booking_id    UUID PRIMARY KEY,
    user_id       VARCHAR(64)   NOT NULL,
    tenant_id     VARCHAR(64)   NOT NULL,
    source_code   VARCHAR(3)    NOT NULL,
    dest_code     VARCHAR(3)    NOT NULL,
    status        VARCHAR(30)   NOT NULL,
    total_amount  NUMERIC(14,2) NOT NULL,
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Indexing strategy -------------------------------------------------------------------
CREATE INDEX idx_flights_route            ON flights (source_airport_id, destination_airport_id);
CREATE INDEX idx_flight_fares_flight_fare ON flight_fares (flight_id, total_fare);
CREATE INDEX idx_hotels_city              ON hotels (city_id);
CREATE INDEX idx_hotel_pricing_city_price ON hotel_pricing (city_id, price_per_night) WHERE is_available;
CREATE INDEX idx_hotel_pricing_hotel      ON hotel_pricing (hotel_id);
CREATE INDEX idx_attractions_city_fee     ON attractions (city_id, entry_fee_per_person);
CREATE INDEX idx_bookings_user            ON bookings (user_id, created_at DESC);
CREATE INDEX idx_bookings_status          ON bookings (status);
CREATE INDEX idx_payment_txn_booking      ON payment_transactions (booking_id);
CREATE INDEX idx_booking_rm_user          ON booking_read_model (user_id);
