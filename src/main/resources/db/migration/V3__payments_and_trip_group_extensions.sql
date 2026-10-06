ALTER TABLE payments ADD COLUMN payer_id UUID NOT NULL;
ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_status_check;
ALTER TABLE payments ADD CONSTRAINT payments_status_check
    CHECK (status IN ('AUTHORIZED', 'CAPTURED', 'REJECTED', 'REFUNDED', 'VOIDED'));
CREATE UNIQUE INDEX uq_payments_active_booking
    ON payments (booking_id) WHERE status IN ('AUTHORIZED', 'CAPTURED');

-- Provisional routing data until Driver Operations & Routing is available.
ALTER TABLE trip_groups ADD COLUMN departure_at TIMESTAMP NULL;
ALTER TABLE trip_groups ADD COLUMN zone VARCHAR(120) NULL;
ALTER TABLE trip_groups ADD COLUMN estimated_distance_km NUMERIC(10, 2) NULL;

CREATE INDEX idx_fares_group_created_at ON fares (group_id, created_at DESC);
