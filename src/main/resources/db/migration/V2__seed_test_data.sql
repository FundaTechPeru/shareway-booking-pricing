-- V2: datos de prueba para desarrollo local.

-- Un grupo con capacidad 4 y 4 cupos disponibles.
INSERT INTO seat_inventories (group_id, capacity, available_seats, version, updated_at)
VALUES ('11111111-1111-1111-1111-111111111111', 4, 4, 0, CURRENT_TIMESTAMP);

-- Una fare_rule vigente: version 1, base 5.00, 1.20 por km, comision 10 %.
INSERT INTO fare_rules (version, base_amount, amount_per_km, platform_fee_pct, valid_from, valid_to)
VALUES (1, 5.00, 1.20, 10.00, DATE '2020-01-01', DATE '2099-12-31');
