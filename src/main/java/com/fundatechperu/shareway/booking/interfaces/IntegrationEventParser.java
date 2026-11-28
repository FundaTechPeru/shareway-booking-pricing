package com.fundatechperu.shareway.booking.interfaces;

import com.fasterxml.jackson.databind.JsonNode;
import com.fundatechperu.shareway.booking.domain.exception.InvalidBookingRequestException;
import com.fundatechperu.shareway.booking.shared.events.BoardingValidated;
import com.fundatechperu.shareway.booking.shared.events.GroupCompleted;
import com.fundatechperu.shareway.booking.shared.events.GroupRejected;
import com.fundatechperu.shareway.booking.shared.events.PaymentAuthorizationFailed;
import com.fundatechperu.shareway.booking.shared.events.PaymentAuthorized;
import com.fundatechperu.shareway.booking.shared.events.ProposalAccepted;
import com.fundatechperu.shareway.booking.shared.events.RouteUpdated;
import com.fundatechperu.shareway.booking.shared.events.TripCancelled;
import com.fundatechperu.shareway.booking.shared.events.TripCompleted;
import com.fundatechperu.shareway.booking.shared.events.TripGroupOpened;
import com.fundatechperu.shareway.booking.shared.events.TripStarted;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Converts the JSON of an integration event, {"eventId", "occurredAt", "type", ...fields}, into
 * the event record its handler consumes. Shared by the internal endpoint (events of Trip
 * Planning and Trip Execution) and the dev-only simulator. An unknown type or a missing or
 * malformed field is an {@link InvalidBookingRequestException} (400).
 * <ul>
 *   <li>{@link Mode#INTERNAL}: eventId, occurredAt and every field of the contract are required
 *       (distanceKm of TripCompleted and reason of TripCancelled may be null).</li>
 *   <li>{@link Mode#DEV}: eventId and occurredAt are generated when absent, the payment and
 *       route events are also accepted, and only the fields the handlers need are required.</li>
 * </ul>
 */
public final class IntegrationEventParser {

    public enum Mode { INTERNAL, DEV }

    public static final List<String> INTERNAL_TYPES = List.of("TripGroupOpened", "GroupCompleted",
            "ProposalAccepted", "GroupRejected", "TripStarted", "BoardingValidated", "TripCompleted", "TripCancelled");
    public static final List<String> DEV_TYPES = List.of("TripGroupOpened", "PaymentAuthorized",
            "PaymentAuthorizationFailed", "GroupCompleted", "ProposalAccepted", "GroupRejected", "RouteUpdated",
            "BoardingValidated", "TripStarted", "TripCompleted", "TripCancelled");

    /** A parsed event together with its envelope. */
    public record ParsedEvent(UUID eventId, Instant occurredAt, String type, Object event) {
    }

    private final Mode mode;
    private final Clock clock;

    public IntegrationEventParser(Mode mode, Clock clock) {
        this.mode = mode;
        this.clock = clock;
    }

    public ParsedEvent parse(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new InvalidBookingRequestException("The event must be a JSON object");
        }
        String type = text(body, "type");
        if (type == null || type.isBlank()) {
            throw new InvalidBookingRequestException("type is required");
        }
        List<String> supported = mode == Mode.INTERNAL ? INTERNAL_TYPES : DEV_TYPES;
        if (!supported.contains(type)) {
            throw new InvalidBookingRequestException("Unsupported event type: " + type
                    + ". Supported: " + String.join(", ", supported));
        }
        Fields fields = new Fields(body, type);
        UUID eventId = mode == Mode.INTERNAL ? fields.uuid("eventId") : fields.optionalUuid("eventId");
        Instant occurredAt = mode == Mode.INTERNAL ? fields.instant("occurredAt") : fields.optionalInstant("occurredAt");
        if (eventId == null) eventId = UUID.randomUUID();
        if (occurredAt == null) occurredAt = clock.instant();
        boolean strict = mode == Mode.INTERNAL;

        Object event = switch (type) {
            case "TripGroupOpened" -> new TripGroupOpened(eventId, occurredAt, fields.uuid("groupId"),
                    fields.capacity());
            case "GroupCompleted" -> new GroupCompleted(eventId, occurredAt, fields.uuid("groupId"));
            case "ProposalAccepted" -> new ProposalAccepted(eventId, occurredAt, fields.uuid("groupId"),
                    fields.uuid("driverId"));
            case "GroupRejected" -> new GroupRejected(eventId, occurredAt, fields.uuid("groupId"),
                    strict ? fields.uuid("driverId") : fields.optionalUuid("driverId"));
            case "TripStarted" -> new TripStarted(eventId, occurredAt, fields.uuid("groupId"),
                    strict ? fields.uuid("tripId") : fields.optionalUuid("tripId"));
            case "BoardingValidated" -> new BoardingValidated(eventId, occurredAt, fields.uuid("bookingId"),
                    strict ? fields.uuid("tripId") : fields.optionalUuid("tripId"));
            case "TripCompleted" -> new TripCompleted(eventId, occurredAt, fields.uuid("groupId"),
                    strict ? fields.uuid("tripId") : fields.optionalUuid("tripId"),
                    strict ? fields.uuid("driverId") : fields.optionalUuid("driverId"),
                    strict ? fields.instant("completedAt") : fields.optionalInstant("completedAt"),
                    fields.optionalDistanceKm(),
                    strict ? fields.uuidList("boardedBookingIds") : fields.optionalUuidList("boardedBookingIds"));
            case "TripCancelled" -> new TripCancelled(eventId, occurredAt, fields.uuid("groupId"),
                    strict ? fields.uuid("tripId") : fields.optionalUuid("tripId"), text(body, "reason"));
            case "PaymentAuthorized" -> new PaymentAuthorized(eventId, occurredAt, fields.uuid("bookingId"),
                    UUID.randomUUID());
            case "PaymentAuthorizationFailed" -> new PaymentAuthorizationFailed(eventId, occurredAt,
                    fields.uuid("bookingId"), text(body, "reason") == null ? "simulated failure" : text(body, "reason"));
            case "RouteUpdated" -> new RouteUpdated(eventId, occurredAt, fields.uuid("groupId"), fields.distanceKm());
            default -> throw new InvalidBookingRequestException("Unsupported event type: " + type);
        };
        return new ParsedEvent(eventId, occurredAt, type, event);
    }

    private static String text(JsonNode body, String field) {
        JsonNode node = body.get(field);
        return node == null || node.isNull() ? null : node.asText();
    }

    /** Typed access to the fields of one event, with the 400 messages. */
    private record Fields(JsonNode body, String type) {

        private JsonNode present(String field) {
            JsonNode node = body.get(field);
            return node == null || node.isNull() ? null : node;
        }

        private InvalidBookingRequestException missing(String field) {
            return new InvalidBookingRequestException(field + " is required for " + type);
        }

        UUID uuid(String field) {
            UUID value = optionalUuid(field);
            if (value == null) throw missing(field);
            return value;
        }

        UUID optionalUuid(String field) {
            JsonNode node = present(field);
            if (node == null) return null;
            return toUuid(node, field);
        }

        private UUID toUuid(JsonNode node, String field) {
            try {
                return UUID.fromString(node.asText());
            } catch (IllegalArgumentException exception) {
                throw new InvalidBookingRequestException(field + " must be a UUID in " + type);
            }
        }

        Instant instant(String field) {
            Instant value = optionalInstant(field);
            if (value == null) throw missing(field);
            return value;
        }

        /** ISO-8601 with offset; a date-time without offset is read as UTC. */
        Instant optionalInstant(String field) {
            JsonNode node = present(field);
            if (node == null) return null;
            String value = node.asText();
            try {
                return OffsetDateTime.parse(value).toInstant();
            } catch (DateTimeParseException withoutOffset) {
                try {
                    return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
                } catch (DateTimeParseException exception) {
                    throw new InvalidBookingRequestException(field + " must be an ISO-8601 date-time in " + type);
                }
            }
        }

        int capacity() {
            JsonNode node = present("capacity");
            if (node == null || !node.canConvertToInt() || node.asInt() < 1) {
                throw new InvalidBookingRequestException("capacity (>= 1) is required for " + type);
            }
            return node.asInt();
        }

        BigDecimal distanceKm() {
            BigDecimal value = optionalDistanceKm();
            if (value == null) throw new InvalidBookingRequestException("distanceKm (>= 0) is required for " + type);
            return value;
        }

        BigDecimal optionalDistanceKm() {
            JsonNode node = present("distanceKm");
            if (node == null) return null;
            BigDecimal value;
            try {
                value = node.isNumber() ? node.decimalValue() : new BigDecimal(node.asText());
            } catch (NumberFormatException exception) {
                throw new InvalidBookingRequestException("distanceKm must be a number in " + type);
            }
            if (value.signum() < 0) throw new InvalidBookingRequestException("distanceKm (>= 0) is required for " + type);
            return value;
        }

        List<UUID> uuidList(String field) {
            List<UUID> value = optionalUuidList(field);
            if (value == null) throw missing(field);
            return value;
        }

        List<UUID> optionalUuidList(String field) {
            JsonNode node = present(field);
            if (node == null) return null;
            if (!node.isArray()) throw new InvalidBookingRequestException(field + " must be an array in " + type);
            List<UUID> ids = new ArrayList<>();
            node.forEach(item -> ids.add(toUuid(item, field)));
            return ids;
        }
    }
}
