# ShareWay Booking & Pricing

Backend microservice for booking shared trips, reserving seats with optimistic locking, managing pricing rules, and estimating fares. `booking` and `pricing` are independent bounded contexts and communicate only through identifiers.

## Requirements

- Java 25
- Maven Wrapper
- PostgreSQL on `localhost:5432`
- Database `shareway-booking-pricing`

Set `DB_PASSWORD` in IntelliJ under **Run Configuration -> Environment variables**. The supported variables are `DB_URL`, `DB_USER`, `DB_PASSWORD`, and `PRICING_PLATFORM_FEE_PERCENT`; `.env.example` contains safe placeholders.

The application uses Flyway migrations and `spring.jpa.hibernate.ddl-auto=validate`. On an empty database, startup applies `V1__create_booking_tables.sql` and `V2__create_pricing_tables.sql`.

## Local database reset

`docs/db/reset-local.sql` is a manual development-only operation. Before running it, verify that the connection is exactly `jdbc:postgresql://localhost:5432/shareway-booking-pricing`. It drops and recreates only the `public` schema:

```bash
psql -h localhost -p 5432 -U postgres -d shareway-booking-pricing \
  -f docs/db/reset-local.sql
```

The same script can be run from an IntelliJ PostgreSQL console. It is never executed automatically by the application.

## API

- `POST /api/v1/bookings` creates a booking and returns `201 Created` with `Location`.
- `GET /api/v1/bookings/{bookingId}` retrieves a booking without exposing hashes.
- `POST /api/v1/bookings/{bookingId}/cancel` cancels a booking and releases its seat.
- `POST /api/v1/pricing/fare-rules` creates a fare rule.
- `GET /api/v1/pricing/fare-rules?zone=` lists rules, optionally filtered by zone.
- `GET /api/v1/pricing/fare-rules/{id}` retrieves a rule.
- `PUT /api/v1/pricing/fare-rules/{id}` updates a rule.
- `GET /api/v1/pricing/fares/estimate?zone=&date=&distanceKm=&passengers=` calculates a fare.
- Swagger UI: `/swagger-ui.html`
- OpenAPI document: `/v3/api-docs`

## Tests

Run the verification suite with:

```bash
./mvnw -q verify
```

Payments, Saga/domain events, Circuit Breaker, and JWT authentication remain future integration work. Payment persistence/domain ports are intentionally not exposed as REST endpoints yet.
