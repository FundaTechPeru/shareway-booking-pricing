package com.fundatechperu.shareway.booking.infrastructure.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundatechperu.shareway.booking.application.port.IntegrationOutbox;
import com.fundatechperu.shareway.booking.domain.model.CancellationReason;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@link IntegrationOutbox} on the outbox_messages table. Each message stores the exact JSON
 * body the receiving service expects; it is written in the caller's transaction
 * (Propagation.MANDATORY) and sent later by {@link OutboxRelay}.
 */
@Component
public class JpaIntegrationOutbox implements IntegrationOutbox {

    private final OutboxMessageRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public JpaIntegrationOutbox(OutboxMessageRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void requestPaymentAuthorization(UUID bookingId, UUID passengerId, UUID paymentMethodId,
                                            BigDecimal amount, String currency) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("bookingId", bookingId.toString());
        body.put("passengerId", passengerId.toString());
        if (paymentMethodId != null) {
            body.put("paymentMethodId", paymentMethodId.toString());   // omitted: Payments picks the card
        }
        body.put("amount", amount);
        body.put("currency", currency == null ? "PEN" : currency);
        store(OutboxMessageType.PAYMENT_AUTHORIZATION, bookingId, body);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void bookingCancelled(UUID bookingId, CancellationReason reason, Instant cancelledAt,
                                 Instant scheduledDepartureAt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("bookingId", bookingId.toString());
        body.put("reason", reason.paymentsReason());
        body.put("cancelledAt", micros(cancelledAt).toString());
        body.put("scheduledDepartureAt", micros(scheduledDepartureAt).toString());
        store(OutboxMessageType.BOOKING_CANCELLED, bookingId, body);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void groupConfirmed(UUID groupId, UUID driverId, List<ConfirmedPassenger> passengers) {
        UUID messageId = UUID.randomUUID();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("idEvento", messageId.toString());
        body.put("grupoId", groupId.toString());
        body.put("conductorId", driverId.toString());
        body.put("pasajeros", passengers.stream().map(passenger -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("reservaId", passenger.bookingId().toString());
            item.put("pasajeroId", passenger.passengerId().toString());
            item.put("codigoHash", passenger.codeHash());
            item.put("pinHash", passenger.pinHash());
            return item;
        }).toList());
        store(messageId, OutboxMessageType.GROUP_CONFIRMED, groupId, body);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void tripCompleted(UUID tripId, UUID driverId, Instant completedAt, List<BookingCharge> bookings) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tripId", tripId.toString());
        body.put("driverId", driverId.toString());
        body.put("completedAt", micros(completedAt).toString());
        body.put("bookings", bookings.stream().map(charge -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("bookingId", charge.bookingId().toString());
            item.put("finalAmount", charge.finalAmount());
            return item;
        }).toList());
        store(OutboxMessageType.TRIP_COMPLETED, tripId, body);
    }

    private void store(OutboxMessageType type, UUID aggregateId, Map<String, Object> body) {
        store(UUID.randomUUID(), type, aggregateId, body);
    }

    private void store(UUID messageId, OutboxMessageType type, UUID aggregateId, Map<String, Object> body) {
        try {
            repository.save(new OutboxMessage(messageId, type, aggregateId, objectMapper.writeValueAsString(body),
                    micros(clock.instant())));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("The " + type + " outbox message could not be serialized", exception);
        }
    }

    /** Database timestamps keep microseconds; the payload and the columns carry the same instant. */
    private static Instant micros(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MICROS);
    }
}
