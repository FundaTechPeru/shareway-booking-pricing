-- V1: esquema Booking & Pricing segun AGENTS.md (seccion "Tablas (Booking DB)").
-- PostgreSQL local, sin Docker. Tipos elegidos donde AGENTS.md no los fija:
--   UUID para ids, INT para contadores, NUMERIC para montos, TIMESTAMP/DATE para fechas.

CREATE TABLE seat_inventories (
    group_id UUID PRIMARY KEY,
    capacity INT NOT NULL,
    available_seats INT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT seat_inventories_available_seats_check CHECK (available_seats BETWEEN 0 AND capacity)
);

CREATE TABLE bookings (
    booking_id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES seat_inventories (group_id),
    request_id UUID NOT NULL UNIQUE,
    passenger_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    pin_hash VARCHAR(255),
    qr_token_hash VARCHAR(255),
    idempotency_key VARCHAR(100) UNIQUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT bookings_status_check CHECK (
        status IN ('requested', 'grouped', 'confirmed', 'boarded', 'in_progress', 'completed', 'cancelled')
    )
);

CREATE INDEX idx_bookings_group_id ON bookings (group_id);

CREATE TABLE booking_sagas (
    saga_id UUID PRIMARY KEY,
    booking_id UUID NOT NULL UNIQUE REFERENCES bookings (booking_id),
    current_step VARCHAR(60) NOT NULL,
    status VARCHAR(20) NOT NULL,
    deadline_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT booking_sagas_status_check CHECK (status IN ('running', 'completed', 'compensated'))
);

CREATE TABLE fare_rules (
    rule_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version INT NOT NULL UNIQUE,
    base_amount NUMERIC(10, 2) NOT NULL,
    amount_per_km NUMERIC(10, 2) NOT NULL,
    platform_fee_pct NUMERIC(5, 2) NOT NULL,
    valid_from DATE NOT NULL,
    valid_to DATE NOT NULL,
    CONSTRAINT fare_rules_amounts_check CHECK (base_amount >= 0 AND amount_per_km >= 0),
    CONSTRAINT fare_rules_platform_fee_pct_check CHECK (platform_fee_pct BETWEEN 0 AND 100)
);

CREATE TABLE fares (
    fare_id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES bookings (booking_id),
    rule_id BIGINT NOT NULL REFERENCES fare_rules (rule_id),
    fare_type VARCHAR(20) NOT NULL,
    distance_km NUMERIC(10, 2) NOT NULL,
    grouped_passengers INT NOT NULL,
    amount NUMERIC(10, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    CONSTRAINT fares_booking_fare_type_unique UNIQUE (booking_id, fare_type),
    CONSTRAINT fares_fare_type_check CHECK (fare_type IN ('estimated', 'final')),
    CONSTRAINT fares_amount_check CHECK (amount >= 0)
);

CREATE TABLE penalties (
    penalty_id UUID PRIMARY KEY,
    booking_id UUID NOT NULL UNIQUE REFERENCES bookings (booking_id),
    amount NUMERIC(10, 2) NOT NULL,
    reason VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT penalties_amount_check CHECK (amount > 0)
);
