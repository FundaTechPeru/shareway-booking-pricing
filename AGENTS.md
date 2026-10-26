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
- Perfiles de Spring: dev (local) y prod (cloud). El endpoint /dev/events solo existe en dev.
- Health checks con Spring Boot Actuator: /actuator/health (con liveness y readiness). Solo health e info expuestos.
- Las migraciones Flyway se aplican solas al arrancar.
- Swagger/OpenAPI disponible en /swagger-ui.html y /v3/api-docs.
- docker-compose.yml local con PostgreSQL y este servicio. Si más adelante se usa RabbitMQ o Redis, se agregan ahí.
- GitHub Actions: compila, ejecuta las pruebas (con un servicio postgres de Actions) y construye la imagen en cada push; publica la imagen solo cuando se sube una etiqueta tipo v0.1.0.
- Rollback: volver a desplegar la imagen con la etiqueta anterior.

## Sprint 2
Completar el ciclo de vida de la reserva: requested -> grouped -> confirmed -> boarded -> in_progress -> completed, y cancelled desde requested, grouped o confirmed. Rutas sin prefijo (/bookings, /fares).

EVENTOS QUE CONSUME (cada uno es idempotente: si la reserva ya está en ese estado o más adelante, se ignora sin error)
- GroupCompleted {groupId}: las reservas requested del grupo pasan a grouped; la saga pasa a current_step SEARCH_DRIVER.
- ProposalAccepted {groupId, driverId}: las reservas grouped del grupo pasan a confirmed; ver CONFIRMACIÓN.
- RouteUpdated {groupId, distanceKm}: recalcula la tarifa estimated de las reservas del grupo con la regla vigente y actualiza la fila de fares (fare_type estimated).
- BoardingValidated {bookingId}: confirmed -> boarded.
- TripStarted {groupId}: las reservas boarded del grupo pasan a in_progress.
- TripCompleted {groupId, distanceKm}: las reservas in_progress pasan a completed; ver CIERRE.
- Ya existentes: TripGroupOpened, PaymentAuthorized, PaymentAuthorizationFailed.

EVENTOS QUE PUBLICA
SeatHeld, PaymentAuthorizationRequested, BookingCancelled (ya existen), BookingConfirmed {bookingId, groupId, passengerId, driverId, boardingPin, qrToken}, FinalFareCalculated {bookingId, amount, currency, ruleVersion}.

CONFIRMACIÓN (US de confirmación de la reserva)
Al confirmar: genera un PIN de 6 dígitos y un token QR aleatorio con una clase BoardingCodeGenerator; guarda en bookings SOLO sus hashes (pin_hash y qr_token_hash, con BCrypt para el PIN y SHA-256 para el token); publica BookingConfirmed con los valores en claro una sola vez (los necesita el servicio de notificaciones) y nunca los devuelvas por la API ni los escribas en logs. La saga pasa a completed.

CIERRE
Al completarse: calcula la tarifa final con la distancia real de TripCompleted usando la misma FarePolicy y la regla vigente, guárdala en fares con fare_type final (UNIQUE con booking_id: si ya existe, no la dupliques), y publica FinalFareCalculated.

CANCELACIÓN Y PENALIDAD (US-12)
POST /bookings/{id}/cancel:
- Solo permitido desde requested, grouped o confirmed. Si ya está cancelled, responde 200 sin cambios (idempotente). Si está boarded, in_progress o completed, responde 409 con mensaje claro. Si no existe, 404.
- Libera el asiento de inmediato (available_seats + 1, sin pasar de capacity), cambia a cancelled, deja la saga en compensated y publica BookingCancelled.
- Si faltan menos de 30 minutos para la salida, crea una penalty con una CancellationPolicy (Strategy): monto = 20 % de la tarifa estimada de la reserva, mínimo 1.00, reason LATE_CANCELLATION. La hora de salida sale de una interfaz DepartureTimeProvider con una implementación simulada configurable por variable de entorno, porque ese dato vive en otro servicio.
- Responde 200 con {bookingId, status: CANCELLED, penaltyAmount (null si no hay), currency}.

TIEMPOS LÍMITE
Un job programado revisa cada minuto las sagas en running con deadline_at vencido y las compensa (cancela la reserva y libera el asiento) con reason NO_DRIVER_AVAILABLE o PAYMENT_TIMEOUT según el paso. Plazos configurables por variables de entorno: pago 15 minutos, esperar grupo 60 minutos, buscar conductor 30 minutos.

HERRAMIENTAS DE PRUEBA
Amplía POST /dev/events (solo perfil dev) para simular todos los eventos de arriba.

SUPUESTOS A DOCUMENTAR EN EL README
Datos de cada evento, 20 % de penalidad, hashes del PIN y QR, plazos, salida simulada y que RouteUpdated no publica evento de aviso.

PRUEBAS (PostgreSQL local, sin Testcontainers)
1. Flujo completo: reservar -> pago autorizado -> GroupCompleted -> ProposalAccepted -> BoardingValidated -> TripStarted -> TripCompleted. Verifica cada estado, que existan los hashes del PIN y QR, la fila fares final y los eventos publicados.
2. Eventos duplicados no cambian nada ni lanzan error.
3. Transición inválida (por ejemplo TripCompleted sobre una reserva requested) se ignora o rechaza sin dañar los datos.
4. Cancelación con más de 30 minutos: sin penalidad, asiento liberado. Con menos de 30 minutos: penalty creada. Cancelar dos veces no libera dos asientos.
5. Cancelar una reserva boarded responde 409.
6. Saga vencida: el job cancela y libera el asiento.
7. RouteUpdated recalcula el monto estimado.
8. Concurrencia: 20 cancelaciones simultáneas de reservas distintas no dejan available_seats por encima de capacity.

DOCUMENTACIÓN Y ENTREGA
- Actualiza OpenAPI: documenta los códigos 200, 404 y 409 del cancel y la cabecera X-User-Id.
- Agrega al README la sección "Sprint 2" con el flujo de estados, los eventos y los supuestos.
- Mantén el CI verde y el Dockerfile funcionando: ejecuta ./mvnw clean verify y dime cuántas pruebas pasan.
- Al final haz una auditoría contra AGENTS.md (estados, eventos, tablas sin cambios de esquema, sin llaves foráneas hacia otros servicios) y corrige lo que difiera.
