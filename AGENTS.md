# Booking & Pricing Service (ShareWay)
Dueño de los cupos (seats), las reservas (bookings), la BookingSaga, las reglas de tarifa y las penalidades.
Stack: Java 21, Spring Boot 3.x, Maven, PostgreSQL local. SIN Docker y SIN RabbitMQ (los eventos pasan por una interfaz EventPublisher con implementación en memoria).

## Reglas
- Todo en inglés: clases, atributos, tablas, columnas, eventos y endpoints.
- Capas: domain (sin Spring ni JPA), application, infrastructure, interfaces (controllers).
- Sin llaves foráneas hacia otros servicios: group_id, passenger_id y request_id son IDs simples (UUID).
- Patrones: Strategy (FarePolicy, CancellationPolicy), State (BookingStatus con transiciones válidas), Saga (BookingSaga como process manager), value object Money.
- Aggregates: SeatInventory, Booking, BookingSaga, FareRule, Fare, Penalty.

## Tablas (Booking DB)
seat_inventories: group_id PK (UUID, sin FK; se crea al consumir el evento TripGroupOpened), capacity, available_seats, version (concurrencia optimista), updated_at. CHECK available_seats BETWEEN 0 AND capacity.
bookings: booking_id PK, group_id FK -> seat_inventories.group_id, request_id UNIQUE, passenger_id, status, pin_hash, qr_token_hash, idempotency_key UNIQUE, version, created_at. CHECK status IN (requested, grouped, confirmed, boarded, in_progress, completed, cancelled). pin_hash y qr_token_hash son nullables (se llenan al confirmar).
booking_sagas: saga_id PK, booking_id FK -> bookings.booking_id UNIQUE, current_step, status, deadline_at, updated_at. CHECK status IN (running, completed, compensated).
fare_rules: rule_id PK, version UNIQUE, base_amount, amount_per_km, platform_fee_pct, valid_from, valid_to. CHECK montos >= 0 y platform_fee_pct BETWEEN 0 AND 100.
fares: fare_id PK, booking_id FK -> bookings, rule_id FK -> fare_rules, fare_type (estimated o final), distance_km, grouped_passengers, amount, currency. UNIQUE (booking_id, fare_type). CHECK amount >= 0.
penalties: penalty_id PK, booking_id FK -> bookings UNIQUE, amount, reason, created_at. CHECK amount > 0.

## Ciclo de vida de la reserva (BookingStatus)
requested: asiento bloqueado y pago solicitado. Si el pago se rechaza pasa a cancelled.
grouped: el grupo se completó (evento GroupCompleted).
confirmed: un conductor aceptó (ProposalAccepted).
boarded: BoardingValidated. in_progress: TripStarted. completed: TripCompleted.
cancelled: pago rechazado, el pasajero canceló o no hubo conductor; libera el asiento. Si cancela con menos de 30 minutos de anticipación se crea una penalty.
En la API el estado se muestra en mayúsculas (REQUESTED); en la base va en minúsculas. Las transiciones inválidas deben lanzar excepción.

## Endpoints
GET /fares/estimate?groupId= -> 200 {amount, currency, ruleVersion}; 404 si el grupo no existe. No guarda nada.
POST /bookings (body: groupId, requestId; cabecera Idempotency-Key) -> 202 con cabecera Location y body {bookingId, status: REQUESTED}; 409 si no hay cupos.
GET /bookings/{id} -> 200 con el estado; 404 si no existe.
POST /bookings/{id}/cancel (opcional, después).
El passenger_id llega en la cabecera X-User-Id (reemplaza al JWT, que valida el API Gateway).

## Eventos
Publica: SeatHeld, PaymentAuthorizationRequested, BookingConfirmed, BookingCancelled, FinalFareCalculated.
Consume (primera fase): TripGroupOpened, PaymentAuthorized, PaymentAuthorizationFailed. Después: GroupCompleted, ProposalAccepted, RouteUpdated, BoardingValidated, TripStarted, TripCompleted.

## Deploy
- Una imagen Docker del servicio, etiquetada con la versión del pom y el hash corto del commit, publicada en GitHub Container Registry (ghcr.io).
- Dockerfile multi-stage con Java 21, usuario no root, y la imagen final solo con el jar.
- Toda configuración (URL, usuario y clave de la base, puerto, perfil) por variables de entorno. Nunca claves en el código ni en el repositorio; solo un .env.example con valores de ejemplo.
- Perfiles de Spring: dev (local) y prod (cub). El endpoint /dev/events solo existe en dev.
- Health checks con Spring Boot Actuator: /actuator/health (con liveness y readiness). Solo health e info expuestos.
- Las migraciones Flyway se aplican solas al arrancar.
- Swagger/OpenAPI disponible en /swagger-ui.html y /v3/api-docs.
- docker-compose.yml local con PostgreSQL y este servicio. Si más adelante se usa RabbitMQ o Redis, se agregan ahí.
- GitHub Actions: compila, ejecuta las pruebas (con un servicio postgres de Actions) y construye la imagen en cada push; publica la imagen solo cuando se sube una etiqueta tipo v0.1.0.
- Rollback: volver a desplegar la imagen con la etiqueta anterior.
