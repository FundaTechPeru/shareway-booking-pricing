-- V3: integration with Trip Planning, Trip Execution and Payments.

-- Booking data needed by the integration:
--   estimated_amount/currency: fare sent to Payments for the authorization (never rewritten);
--   payment_id/payment_authorized_at: set when Payments authorizes the payment;
--   boarding_pin/qr_token: clear boarding codes shown to the passenger (only to the owner);
--   cancellation_reason/cancelled_at: why and when the booking was cancelled.
ALTER TABLE bookings
    ADD COLUMN estimated_amount NUMERIC(10, 2),
    ADD COLUMN currency VARCHAR(3),
    ADD COLUMN payment_id UUID,
    ADD COLUMN payment_authorized_at TIMESTAMP,
    ADD COLUMN boarding_pin VARCHAR(6),
    ADD COLUMN qr_token VARCHAR(64),
    ADD COLUMN cancellation_reason VARCHAR(30),
    ADD COLUMN cancelled_at TIMESTAMP;

CREATE INDEX idx_bookings_passenger_id ON bookings (passenger_id);

-- Idempotency of POST /api/v1/internal/events: one row per event already processed.
CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    type VARCHAR(50) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Transactional outbox of the calls to Payments and Trip Execution. sequence_number keeps the
-- order in which the messages were stored; the relay sends them in that order.
CREATE TABLE outbox_messages (
    id UUID PRIMARY KEY,
    sequence_number BIGINT GENERATED ALWAYS AS IDENTITY,
    type VARCHAR(40) NOT NULL,
    aggregate_id UUID,
    payload TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE,
    failed_at TIMESTAMP WITH TIME ZONE,
    last_error VARCHAR(1000),
    CONSTRAINT outbox_messages_sequence_number_unique UNIQUE (sequence_number),
    CONSTRAINT outbox_messages_type_check CHECK (
        type IN ('PAYMENT_AUTHORIZATION', 'BOOKING_CANCELLED', 'GROUP_CONFIRMED', 'TRIP_COMPLETED')
    )
);

CREATE INDEX idx_outbox_messages_pending
    ON outbox_messages (sequence_number)
    WHERE processed_at IS NULL AND failed_at IS NULL;

-- Fare catalogue baseline: version 1, base 5.00, 1.20 per km, 10 % platform fee. Only inserted
-- when the catalogue is empty; V2 already seeds this same rule, so with V2 applied it does nothing.
INSERT INTO fare_rules (version, base_amount, amount_per_km, platform_fee_pct, valid_from, valid_to)
SELECT 1, 5.00, 1.20, 10.00, DATE '2020-01-01', DATE '2099-12-31'
WHERE NOT EXISTS (SELECT 1 FROM fare_rules);

-- The demo group seeded by V2 is no longer needed: Trip Planning opens the groups (TripGroupOpened).
DELETE FROM seat_inventories
WHERE group_id = '11111111-1111-1111-1111-111111111111'
  AND NOT EXISTS (SELECT 1 FROM bookings WHERE group_id = '11111111-1111-1111-1111-111111111111');
