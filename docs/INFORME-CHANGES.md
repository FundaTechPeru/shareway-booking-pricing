# TB2 architecture changes

The implementation adds the following changes to the architecture report:

- `fare_rules` replaces the former Spanish fare table and uses `FareRuleStatus.ACTIVE` or `INACTIVE`.
- `payments` now contains `payer_id` and accepts `VOIDED` for an authorization cancelled without penalty.
- `trip_groups` contains provisional `departure_at`, `zone`, and `estimated_distance_km` fields until Driver Operations & Routing supplies authoritative values.
- `bookings` contains nullable `payment_authorized_at` for the reservation saga.
- `processed_events(event_id, handler)` is the idempotency boundary for event consumers.
- The neutral `shared.events` package contains immutable contracts used by both bounded contexts.
- Payment authorization is initiated by the booking saga after reservation creation; seats remain reserved in `REQUESTED` until authorization.
- Fare calculations are recorded through `FareService.recordFare` and remain idempotent for the same group, rule version, and calculated amounts.

## Documented provisional decisions

Passenger count for saga fare authorization is provisionally the greater of current occupied seats and `minPassengers`. The group routing fields are provisional because the Driver Operations & Routing service is not part of this repository.

## Pending

Durable event delivery with an Outbox/Event Bus, full saga handlers, notification delivery for clear-text PIN/QR values, production payment provider integration, a complete Circuit Breaker, JWT ownership checks for every aggregate, and the 100-iteration PostgreSQL concurrency suite require the external services or Docker runtime not available in this environment.
