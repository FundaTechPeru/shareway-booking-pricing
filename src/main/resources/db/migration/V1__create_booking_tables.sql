CREATE TABLE trip_requests (
    request_id UUID PRIMARY KEY,
    passenger_id UUID NOT NULL,
    origin_lat NUMERIC(9, 6) NOT NULL,
    origin_lng NUMERIC(9, 6) NOT NULL,
    destination_lat NUMERIC(9, 6) NOT NULL,
    destination_lng NUMERIC(9, 6) NOT NULL,
    trip_date DATE NOT NULL,
    window_start TIME NOT NULL,
    window_end TIME NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT trip_requests_window_check CHECK (window_start < window_end)
);

CREATE INDEX idx_trip_requests_date_status ON trip_requests (trip_date, status);

CREATE TABLE trip_groups (
    group_id UUID PRIMARY KEY,
    capacity INT NOT NULL,
    min_passengers INT NOT NULL,
    available_seats INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT trip_groups_capacity_check CHECK (capacity > 0),
    CONSTRAINT trip_groups_available_seats_check CHECK (available_seats BETWEEN 0 AND capacity),
    CONSTRAINT trip_groups_min_passengers_check CHECK (min_passengers <= capacity)
);

CREATE INDEX idx_trip_groups_status ON trip_groups (status);

CREATE TABLE bookings (
    booking_id UUID PRIMARY KEY,
    request_id UUID NOT NULL UNIQUE REFERENCES trip_requests(request_id),
    group_id UUID NOT NULL REFERENCES trip_groups(group_id),
    status VARCHAR(20) NOT NULL,
    pin_hash VARCHAR(255),
    qr_token_hash VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_bookings_group_id ON bookings (group_id);
CREATE INDEX idx_bookings_status ON bookings (status);
