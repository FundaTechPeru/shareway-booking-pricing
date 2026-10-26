package com.fundatechperu.shareway.booking;

import com.fundatechperu.shareway.booking.infrastructure.web.GlobalExceptionHandler;
import com.fundatechperu.shareway.booking.interfaces.DevEventController;
import com.fundatechperu.shareway.booking.shared.events.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /dev/events (dev profile only): publishes a simulated event through the same
 * EventPublisher used by the application. Every consumed event of the lifecycle can be
 * simulated (Sprint 2).
 */
class DevEventControllerTest {

    private final EventPublisher eventPublisher = mock(EventPublisher.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DevEventController(eventPublisher))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private Object publishAndCapture(String json) throws Exception {
        mockMvc.perform(post("/dev/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isAccepted());
        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publish(events.capture());
        return events.getValue();
    }

    @Test
    void publishesPaymentAuthorizationFailedWithTheInformedBookingId() throws Exception {
        UUID bookingId = UUID.randomUUID();

        Object captured = publishAndCapture(
                "{\"type\":\"PaymentAuthorizationFailed\",\"bookingId\":\"" + bookingId + "\"}");

        PaymentAuthorizationFailed event = (PaymentAuthorizationFailed) captured;
        assertEquals(bookingId, event.bookingId());
        assertEquals("simulated failure", event.reason());
        assertNotNull(event.eventId());
    }

    @Test
    void publishesPaymentAuthorized() throws Exception {
        UUID bookingId = UUID.randomUUID();

        Object captured = publishAndCapture(
                "{\"type\":\"PaymentAuthorized\",\"bookingId\":\"" + bookingId + "\"}");

        PaymentAuthorized event = (PaymentAuthorized) captured;
        assertEquals(bookingId, event.bookingId());
        assertNotNull(event.paymentId());
    }

    @Test
    void publishesTripGroupOpenedWithCapacity() throws Exception {
        UUID groupId = UUID.randomUUID();

        Object captured = publishAndCapture(
                "{\"type\":\"TripGroupOpened\",\"groupId\":\"" + groupId + "\",\"capacity\":4}");

        TripGroupOpened event = (TripGroupOpened) captured;
        assertEquals(groupId, event.groupId());
        assertEquals(4, event.capacity());
    }

    @Test
    void publishesGroupCompletedAndTripStarted() throws Exception {
        UUID groupId = UUID.randomUUID();

        Object group = publishAndCapture("{\"type\":\"GroupCompleted\",\"groupId\":\"" + groupId + "\"}");
        assertEquals(groupId, ((GroupCompleted) group).groupId());

        reset(eventPublisher);
        Object started = publishAndCapture("{\"type\":\"TripStarted\",\"groupId\":\"" + groupId + "\"}");
        assertEquals(groupId, ((TripStarted) started).groupId());
    }

    @Test
    void publishesProposalAcceptedWithTheDriver() throws Exception {
        UUID groupId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();

        Object captured = publishAndCapture("{\"type\":\"ProposalAccepted\",\"groupId\":\"" + groupId
                + "\",\"driverId\":\"" + driverId + "\"}");

        ProposalAccepted event = (ProposalAccepted) captured;
        assertEquals(groupId, event.groupId());
        assertEquals(driverId, event.driverId());
    }

    @Test
    void publishesRouteUpdatedWithTheDistance() throws Exception {
        UUID groupId = UUID.randomUUID();

        Object captured = publishAndCapture("{\"type\":\"RouteUpdated\",\"groupId\":\"" + groupId
                + "\",\"distanceKm\":20.5}");

        RouteUpdated event = (RouteUpdated) captured;
        assertEquals(groupId, event.groupId());
        assertEquals(0, new BigDecimal("20.5").compareTo(event.distanceKm()));
    }

    @Test
    void publishesBoardingValidated() throws Exception {
        UUID bookingId = UUID.randomUUID();

        Object captured = publishAndCapture(
                "{\"type\":\"BoardingValidated\",\"bookingId\":\"" + bookingId + "\"}");

        assertEquals(bookingId, ((BoardingValidated) captured).bookingId());
    }

    @Test
    void publishesTripCompletedWithTheRealDistance() throws Exception {
        UUID groupId = UUID.randomUUID();

        Object captured = publishAndCapture("{\"type\":\"TripCompleted\",\"groupId\":\"" + groupId
                + "\",\"distanceKm\":18}");

        TripCompleted event = (TripCompleted) captured;
        assertEquals(groupId, event.groupId());
        assertEquals(0, new BigDecimal("18").compareTo(event.distanceKm()));
    }

    @Test
    void unknownEventTypeIsA400() throws Exception {
        mockMvc.perform(post("/dev/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"NotAnEvent\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Unsupported event type: NotAnEvent. Supported: TripGroupOpened, PaymentAuthorized, "
                                + "PaymentAuthorizationFailed, GroupCompleted, ProposalAccepted, RouteUpdated, "
                                + "BoardingValidated, TripStarted, TripCompleted"));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void paymentEventsRequireBookingId() throws Exception {
        mockMvc.perform(post("/dev/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PaymentAuthorizationFailed\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("bookingId is required for PaymentAuthorizationFailed"));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void tripGroupOpenedRequiresGroupIdAndCapacity() throws Exception {
        mockMvc.perform(post("/dev/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"TripGroupOpened\",\"groupId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("capacity (>= 1) is required for TripGroupOpened"));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void proposalAcceptedRequiresTheDriver() throws Exception {
        mockMvc.perform(post("/dev/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ProposalAccepted\",\"groupId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("driverId is required for ProposalAccepted"));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void tripCompletedRequiresTheDistance() throws Exception {
        mockMvc.perform(post("/dev/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"TripCompleted\",\"groupId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("distanceKm (>= 0) is required for TripCompleted"));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void blankTypeIsA400() throws Exception {
        mockMvc.perform(post("/dev/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(eventPublisher);
    }
}
