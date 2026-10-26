package com.fundatechperu.shareway.booking;

import com.fundatechperu.shareway.booking.infrastructure.web.GlobalExceptionHandler;
import com.fundatechperu.shareway.booking.interfaces.DevEventController;
import com.fundatechperu.shareway.booking.shared.events.EventPublisher;
import com.fundatechperu.shareway.booking.shared.events.PaymentAuthorizationFailed;
import com.fundatechperu.shareway.booking.shared.events.PaymentAuthorized;
import com.fundatechperu.shareway.booking.shared.events.TripGroupOpened;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /dev/events (dev profile only): publishes a simulated event through the same
 * EventPublisher used by the application.
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

    @Test
    void publishesPaymentAuthorizationFailedWithTheInformedBookingId() throws Exception {
        UUID bookingId = UUID.randomUUID();

        mockMvc.perform(post("/dev/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PaymentAuthorizationFailed\",\"bookingId\":\"" + bookingId + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.type").value("PaymentAuthorizationFailed"))
                .andExpect(jsonPath("$.published").value(true));

        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publish(events.capture());
        PaymentAuthorizationFailed event = (PaymentAuthorizationFailed) events.getValue();
        assertEquals(bookingId, event.bookingId());
        assertEquals("simulated failure", event.reason());
        assertNotNull(event.eventId());
    }

    @Test
    void publishesPaymentAuthorized() throws Exception {
        UUID bookingId = UUID.randomUUID();

        mockMvc.perform(post("/dev/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PaymentAuthorized\",\"bookingId\":\"" + bookingId + "\"}"))
                .andExpect(status().isAccepted());

        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publish(events.capture());
        PaymentAuthorized event = (PaymentAuthorized) events.getValue();
        assertEquals(bookingId, event.bookingId());
        assertNotNull(event.paymentId());
    }

    @Test
    void publishesTripGroupOpenedWithCapacity() throws Exception {
        UUID groupId = UUID.randomUUID();

        mockMvc.perform(post("/dev/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"TripGroupOpened\",\"groupId\":\"" + groupId + "\",\"capacity\":4}"))
                .andExpect(status().isAccepted());

        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publish(events.capture());
        TripGroupOpened event = (TripGroupOpened) events.getValue();
        assertEquals(groupId, event.groupId());
        assertEquals(4, event.capacity());
    }

    @Test
    void unknownEventTypeIsA400() throws Exception {
        mockMvc.perform(post("/dev/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"TripCompleted\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Unsupported event type: TripCompleted. Supported: PaymentAuthorized, PaymentAuthorizationFailed, TripGroupOpened"));
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
    void blankTypeIsA400() throws Exception {
        mockMvc.perform(post("/dev/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(eventPublisher);
    }
}
