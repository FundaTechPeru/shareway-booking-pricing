# ShareWay Booking & Pricing

Backend microservice for booking shared trips, reserving seats with optimistic locking, managing pricing rules, and estimating fares. `booking` and `pricing` are independent bounded contexts and communicate only through identifiers.

## Requirements

- Java 21
- Maven Wrapper
- PostgreSQL on `localhost:5432`
- Development database `shareway-booking-pricing`

All environment-specific configuration comes from environment variables: `DB_URL`, `DB_USER`, `DB_PASSWORD` and `SERVER_PORT`. Defaults exist **only** in the `dev` profile (`application-dev.yml`), which is also the only profile that imports the optional local `.env` file: copy `.env.example` to `.env` and set `DB_PASSWORD`. `.env` is ignored by Git and must never be committed. Environment variables supplied by the process take precedence over the file.

The `prod` profile (`application-prod.yml`) has no defaults at all, so a cloud deployment must set `SPRING_PROFILES_ACTIVE=prod` together with the four variables. For IntelliJ, either configure them under **Run Configuration -> Environment variables** or keep them in the local `.env` file and launch with the `dev` profile. The platform fee is not configurable per environment, it comes from the `fare_rules.platform_fee_pct` column of the active fare rule.

```powershell
./mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

The application uses Flyway migrations (`V1__create_booking_tables` schema plus `V2__seed_test_data`) and `spring.jpa.hibernate.ddl-auto=validate`.

## Integration-test database

Integration tests use local PostgreSQL only, never the development database. Create the separate test database once:

```powershell
psql -h localhost -p 5432 -U postgres -d postgres `
  -c 'CREATE DATABASE "shareway-booking-pricing-test";'
```

Define `DB_PASSWORD` for the development application. Define `TEST_DB_PASSWORD` as well when the test database uses a different password. Tests accept `TEST_DB_URL` and `TEST_DB_USER`, but the safe default is `jdbc:postgresql://localhost:5432/shareway-booking-pricing-test`. The test profile configures a 30-connection Hikari pool and aborts if the URL does not point to `localhost` and a database ending in `-test`.

Run the full suite from a terminal:

```powershell
$env:DB_PASSWORD = "your-development-password"
$env:TEST_DB_PASSWORD = "your-test-password"
./mvnw.cmd -q verify
```

In IntelliJ, open the test Run Configuration, set the same environment variables (`DB_PASSWORD`, `TEST_DB_PASSWORD`, and optionally `TEST_DB_URL`/`TEST_DB_USER`), and run the tests. Do not point test variables to `shareway-booking-pricing`.

## Local database reset

`docs/db/reset-local.sql` is a manual development-only operation. Before running it, verify that the connection is exactly `jdbc:postgresql://localhost:5432/shareway-booking-pricing`. It drops and recreates only the `public` schema:

```bash
psql -h localhost -p 5432 -U postgres -d shareway-booking-pricing \
  -f docs/db/reset-local.sql
```

The same script can be run from an IntelliJ PostgreSQL console. It is never executed automatically by the application.

## API

- `POST /bookings` (body `groupId` + `requestId`; cabeceras `X-User-Id` e `Idempotency-Key`) returns `202 Accepted` with `Location` and `{bookingId, status: REQUESTED}`. In one local transaction it holds a seat of `seat_inventories` with an optimistic lock, creates the booking in `requested`, records the `estimated` fare, starts the booking saga (`running`, `current_step=AUTHORIZE_PAYMENT`, `deadline_at=now+15min`) and publishes `SeatHeld` + `PaymentAuthorizationRequested`. Replaying the same `Idempotency-Key` returns the original booking without creating another one. `400` if `groupId`, `requestId` or `X-User-Id` is missing; `404` if the group is unknown or no fare rule is valid today; `409` with `"El viaje ya no tiene asientos disponibles."` when there are no seats or the optimistic lock keeps failing after `booking.reservation.max-attempts` retries.
- `GET /bookings/{bookingId}` returns `200 {bookingId, status}` (status in uppercase) or `404`.
- `POST /bookings/{bookingId}/cancel` cancels a booking and releases its seat. The passenger comes from `X-User-Id`: the owner gets `200`, a missing header `400` and another passenger `404`.
- `POST /dev/events` (only with the `dev` profile) publishes a simulated event through the same in-memory bus; see [Simulated events](#simulated-events-dev-profile).
- `GET /fare-rules` lists the fare rules.
- `GET /fare-rules/{id}` retrieves one rule. The contract exposes **only GET** for fare rules: there is no `POST` and no `PUT`.
- `GET /fares/estimate?groupId=` returns `200 {amount, currency, ruleVersion}`; `404` when the group has no seat inventory or no fare rule is valid today. Read-only: nothing is persisted.
- Swagger UI: `/swagger-ui.html`
- OpenAPI document: `/v3/api-docs`
- Health: `GET /actuator/health` with liveness and readiness (`/actuator/health/liveness`, `/actuator/health/readiness`) and `GET /actuator/info`. Only `health` and `info` are exposed; no other actuator endpoint exists.

This service performs no authentication: the passenger comes from the `X-User-Id` header, which the API Gateway validates before forwarding it. `POST /bookings` answers `400` when that header is missing. No JWT scheme is exposed in the OpenAPI document.

> **Nota:** Este servicio debe quedar detrás del API Gateway; no exponerlo directamente a internet.

## Fare estimate assumptions

`GET /fares/estimate?groupId=` is priced by the `FarePolicy` strategy (`DistanceBasedFarePolicy`). Until the neighbouring services land, the endpoint runs on these assumptions:

- **Rule selection**: the active rule is the highest-`version` `fare_rules` row whose `valid_from`/`valid_to` range includes today. If none is valid, the endpoint answers `404`.
- **Formula**: `amount = farePerPassenger + platformFee`, where `farePerPassenger = (base_amount + amount_per_km * distance_km) / passengers` and `platformFee = farePerPassenger * platform_fee_pct / 100`. Every money step rounds `HALF_UP` to 2 decimals.
- **Distance comes from another service**: Driver Operations & Routing owns the route. While it has no endpoint, `GroupInfoProvider` is satisfied by `FakeGroupInfoProvider`, which quotes every group with a fixed **12 km**.
- **Passengers are local**: `groupedPassengers = seat_inventories.capacity - seat_inventories.available_seats` (seats already held), with a **minimum of 1** — an empty group is quoted as a solo rider, as required by the strategy.
- **Currency**: `PEN` (the `Money` value object default; `fare_rules` has no currency column).
- **Read-only**: the estimate never writes a `fares` row; that row belongs to the booking flow (`estimated`/`final` per booking).
- **`404` semantics**: unknown `groupId` (no `seat_inventories` row) or no valid fare rule. There is no `422`: the price of a missing configuration is "unknown", not "unprocessable".

## Booking saga

```mermaid
sequenceDiagram
    Passenger->>Booking: POST /bookings
    Booking->>Booking: saga running / AUTHORIZE_PAYMENT (deadline +15 min)
    Booking-->>Payment Provider: PaymentAuthorizationRequested
    Payment Provider-->>Booking: PaymentAuthorized (simulated via POST /dev/events)
    Booking->>Booking: saga WAIT_GROUP (still running)
    Note over Booking: PaymentAuthorizationFailed → compensation:
    Booking->>Booking: booking cancelled + seat released
    Booking->>Booking: saga compensated + BookingCancelled published
```

Events travel through the `EventPublisher` interface with an in-memory implementation (`InMemoryEventPublisher`): each publish dispatches synchronously to every registered `EventHandler` that supports the event type.

| Event | Handler | Effect (one local transaction each) |
|---|---|---|
| `TripGroupOpened` | `TripGroupOpenedHandler` | Creates the `seat_inventory` (`available_seats = capacity`). Re-delivery is a no-op. |
| `PaymentAuthorized` | `PaymentAuthorizedHandler` | Saga advances to `WAIT_GROUP` and stays `running`. Ignored if the saga already finished. |
| `PaymentAuthorizationFailed` | `PaymentAuthorizationFailedHandler` | Compensates: booking → `cancelled`, seat released (`available_seats + 1`), saga → `compensated`, publishes `BookingCancelled`. Re-delivery does not release a second seat. |

Published by `POST /bookings`: `SeatHeld` and `PaymentAuthorizationRequested`. Still pending: `BookingConfirmed`, `FinalFareCalculated` and the remaining consumers.

Events live only in this JVM process and are lost if the application stops during processing; a durable Event Bus with retries and an Outbox is future work.

### Simulated events (dev profile)

To exercise the handlers by hand, start the application with the `dev` profile and call `POST /dev/events` (the controller does not exist in any other profile):

```powershell
./mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

```bash
# open a group (creates seat_inventories with 4 seats)
curl -X POST localhost:8080/dev/events -H "Content-Type: application/json" \
  -d '{"type":"TripGroupOpened","groupId":"11111111-1111-1111-1111-111111111111","capacity":4}'

# happy path: payment authorized -> saga WAIT_GROUP
curl -X POST localhost:8080/dev/events -H "Content-Type: application/json" \
  -d '{"type":"PaymentAuthorized","bookingId":"<booking-id>"}'

# compensation: booking cancelled, seat +1, saga compensated, BookingCancelled published
curl -X POST localhost:8080/dev/events -H "Content-Type: application/json" \
  -d '{"type":"PaymentAuthorizationFailed","bookingId":"<booking-id>","reason":"card declined"}'
```

`400` for an unsupported `type` or missing required fields; `404` for an unknown `bookingId`/`groupId`.

## Tests

Run the verification suite with local PostgreSQL:

```bash
./mvnw -q verify
```

It runs the unit tests plus two PostgreSQL-backed tests (no Testcontainers): `SharewayBookingPricingApplicationTests` (Flyway migrations + Hibernate validation against the real schema) and `BookingIntegrationTest`, which starts the real HTTP server and exercises the whole flow end to end:

1. **Last seat**: a group with `available_seats = 1` and 20 simultaneous `POST /bookings` with distinct `Idempotency-Key` — exactly one `202` and nineteen `409` (`"El viaje ya no tiene asientos disponibles."`), ending with `available_seats = 0`.
2. **Idempotent retry**: the same `Idempotency-Key` returns the same booking and `available_seats` only drops once.
3. **Rejected payment**: after `PaymentAuthorizationFailed` the booking is `CANCELLED`, the saga `compensated` and `available_seats` returns to its original value.

The test schema is reset per class (Flyway clean + migrate, `V2` seed included) and the booking tables are truncated per test method. `DB_PASSWORD` is required (`TEST_DB_PASSWORD` too when the test database uses another password); see [Integration-test database](#integration-test-database).

Events are dispatched by the in-memory `EventPublisher` and are lost if the application stops during processing. A durable event bus with retries and an Outbox remains future work. Payment REST integration, `BookingConfirmed` / `FinalFareCalculated` publication, notification delivery, and Circuit Breaker remain pending.

## Deployment

### Run locally with docker compose

`docker-compose.yml` starts two services: `postgres` (image `postgres:16`, database `shareway_booking`, named volume `postgres-data`, healthcheck with `pg_isready`) and `booking-pricing`, built from the multi-stage `Dockerfile`. The application starts only after PostgreSQL reports healthy, and Flyway applies the migrations automatically on startup.

```powershell
copy .env.example .env   # set DB_PASSWORD first
docker compose up --build
```

Every sensitive value comes from `.env` (`DB_PASSWORD` is mandatory; `DB_USER` and `SERVER_PORT` are optional). `.env` is ignored by Git and by the Docker build context, so no secret reaches the repository or the image. The container receives `DB_URL`, `DB_USER`, `DB_PASSWORD`, `SPRING_PROFILES_ACTIVE=dev` and `SERVER_PORT`, and its port is mapped to the host (`8080:8080` with the example values).

Stop the stack with `docker compose down`; add `-v` to also delete the `postgres-data` volume.

### Environment variables

No real values are stored in this repository: only `.env.example` carries placeholders, and the real `.env` is never committed.

| Variable | Required | Purpose | Example value |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Yes | `dev` (local) or `prod` (cloud) | `dev` |
| `DB_URL` | Yes (no default outside `dev`) | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/shareway_booking` |
| `DB_USER` | Yes (no default outside `dev`) | Database user | `postgres` |
| `DB_PASSWORD` | Yes | Database password | *(no example value: set it only in your local `.env`)* |
| `SERVER_PORT` | Yes (no default outside `dev`) | HTTP port of the service | `8080` |
| `TEST_DB_URL`, `TEST_DB_USER`, `TEST_DB_PASSWORD` | Optional | Override the integration-test database | `jdbc:postgresql://localhost:5432/<name>-test` |
| `BOOKING_RESERVATION_MAX_ATTEMPTS`, `BOOKING_RESERVATION_BACKOFF_MS` | Optional | Optimistic-lock retry policy | `3`, `20` |
| `BOOKING_CANCELLATION_PENALTY_WINDOW_MINUTES`, `BOOKING_SAGA_PAYMENT_TIMEOUT_MINUTES` | Optional | Cancellation penalty window and saga deadline | `30`, `15` |

Only `dev` supplies defaults for the first four rows; `prod` reads every value from the environment. The platform fee is not configurable: it comes from `fare_rules.platform_fee_pct`.

### Health and Swagger URLs

- Health: `http://localhost:8080/actuator/health`
- Liveness: `http://localhost:8080/actuator/health/liveness`
- Readiness: `http://localhost:8080/actuator/health/readiness`
- Info: `http://localhost:8080/actuator/info`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI document: `http://localhost:8080/v3/api-docs`

Only `health` and `info` are exposed by Actuator. Replace `localhost:8080` with the host and port where the service is published.

### Publish the image

The image is built from the multi-stage `Dockerfile` (Maven + JDK 21 to produce the jar, JRE 21 + jar only to run it, non-root user) and is tagged with **the version of `pom.xml` plus the short commit hash**, published to GitHub Container Registry:

```bash
VERSION=$(mvn -q -DforceStdout help:evaluate -Dexpression=project.version | grep -v '\[INFO\]')
HASH=$(git rev-parse --short HEAD)
IMAGE="ghcr.io/<organization>/shareway-booking-pricing:${VERSION}-${HASH}"

docker build -t "$IMAGE" .
docker push "$IMAGE"          # after: docker login ghcr.io (token with write:packages)
```

GitHub Actions compiles the service, runs the test suite against its own PostgreSQL service and builds the image on **every push**; the image is published **only** when a version tag such as `v0.1.0` is pushed, which keeps `ghcr.io` free of images from unfinished work.

### Rollback

Rollback means **redeploying the image with the previous version tag**: no rebuild, no code change.

1. List the published tags (`Packages` in GitHub, or `docker images ghcr.io/<organization>/shareway-booking-pricing`) and pick the previous one, e.g. `0.0.1-SNAPSHOT-<previous-hash>`.
2. Pull and deploy that exact tag with the same environment variables:

```bash
PREVIOUS="ghcr.io/<organization>/shareway-booking-pricing:0.0.1-SNAPSHOT-<previous-hash>"
docker pull "$PREVIOUS"
docker run -d --name shareway-booking-pricing -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_URL="$DB_URL" -e DB_USER="$DB_USER" -e DB_PASSWORD="$DB_PASSWORD" -e SERVER_PORT=8080 \
  "$PREVIOUS"
```

With `docker-compose.yml`, point the `image:` of `booking-pricing` at `$PREVIOUS` and run `docker compose up -d --no-build`. The `postgres-data` volume is untouched, so application data survives the rollback; Flyway migrations are forward-only, so only roll back to an image whose schema is compatible with the current database.

### Evidence

| Elemento | Evidencia |
|---|---|
| Versión evaluada | `pom.xml` version `0.0.1-SNAPSHOT` on commit `67a6867` |
| Entorno local | `Dockerfile` (multi-stage, non-root), `docker-compose.yml` (postgres 16 + booking-pricing, healthcheck `pg_isready`), `.dockerignore`, `.env.example`, `.gitignore` ignores `.env` |
| Ejecución | `./mvnw clean verify` → `BUILD SUCCESS`, 84 tests, 0 failures · `docker compose up --build` (requires Docker on the machine) |
| Documentación (URL de Swagger) | `http://localhost:8080/swagger-ui.html` (OpenAPI: `http://localhost:8080/v3/api-docs`) |
