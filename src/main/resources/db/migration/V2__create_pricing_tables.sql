CREATE TABLE fare_rules (
    fare_rule_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    zone VARCHAR(120) NOT NULL,
    base_distance_km NUMERIC(10, 2) NOT NULL,
    base_price NUMERIC(10, 2) NOT NULL,
    price_per_km NUMERIC(10, 2) NOT NULL,
    valid_from DATE NOT NULL,
    valid_until DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    rule_version INT NOT NULL DEFAULT 1,
    CONSTRAINT fare_rules_amounts_check CHECK (
        base_distance_km >= 0 AND base_price >= 0 AND price_per_km >= 0
    ),
    CONSTRAINT fare_rules_dates_check CHECK (valid_from <= valid_until)
);

CREATE INDEX idx_fare_rules_zone_status ON fare_rules (zone, status);

CREATE TABLE fares (
    fare_id UUID PRIMARY KEY,
    group_id UUID NOT NULL,
    fare_rule_id BIGINT NOT NULL REFERENCES fare_rules(fare_rule_id),
    rule_version INT NOT NULL,
    base_amount NUMERIC(10, 2) NOT NULL,
    amount_per_passenger NUMERIC(10, 2) NOT NULL,
    platform_fee NUMERIC(10, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fares_amounts_check CHECK (
        base_amount >= 0 AND amount_per_passenger >= 0 AND platform_fee >= 0
    )
);

CREATE TABLE payments (
    payment_id UUID PRIMARY KEY,
    booking_id UUID NOT NULL,
    amount NUMERIC(10, 2) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'PEN',
    status VARCHAR(20) NOT NULL,
    provider_ref VARCHAR(100) UNIQUE,
    authorized_at TIMESTAMP,
    captured_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT payments_status_check CHECK (status IN ('AUTHORIZED', 'CAPTURED', 'REJECTED', 'REFUNDED')),
    CONSTRAINT payments_amount_check CHECK (amount >= 0)
);

CREATE TABLE penalties (
    penalty_id UUID PRIMARY KEY,
    booking_id UUID NOT NULL,
    amount NUMERIC(10, 2) NOT NULL,
    reason VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT penalties_amount_check CHECK (amount >= 0)
);

CREATE TABLE driver_settlements (
    settlement_id UUID PRIMARY KEY,
    trip_id UUID NOT NULL,
    driver_id UUID NOT NULL,
    gross_amount NUMERIC(10, 2) NOT NULL,
    commission NUMERIC(10, 2) NOT NULL,
    net_amount NUMERIC(10, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    CONSTRAINT driver_settlements_amounts_check CHECK (gross_amount = commission + net_amount)
);
