ALTER TABLE bookings ADD COLUMN payment_authorized_at TIMESTAMP NULL;

CREATE TABLE processed_events (
    event_id UUID NOT NULL,
    handler VARCHAR(200) NOT NULL,
    processed_at TIMESTAMP NOT NULL,
    PRIMARY KEY (event_id, handler)
);
