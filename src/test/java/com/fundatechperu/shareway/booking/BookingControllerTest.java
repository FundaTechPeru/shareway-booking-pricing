package com.fundatechperu.shareway.booking;

import com.fundatechperu.shareway.booking.application.dto.request.CreateBookingRequest;
import com.fundatechperu.shareway.booking.application.dto.response.BookingResponse;
import com.fundatechperu.shareway.booking.application.service.BookingService;
import com.fundatechperu.shareway.booking.domain.exception.BookingNotFoundException;
import com.fundatechperu.shareway.booking.domain.exception.InvalidBookingRequestException;
import com.fundatechperu.shareway.booking.domain.exception.NoSeatsAvailableException;
import com.fundatechperu.shareway.booking.domain.model.BookingStatus;
import com.fundatechperu.shareway.booking.infrastructure.web.GlobalExceptionHandler;
import com.fundatechperu.shareway.booking.interfaces.BookingController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HTTP contract of POST /bookings and GET /bookings/{id}: 202 + Location, 400, 404 and
 * the 409 body with "El viaje ya no tiene asientos disponibles."
 */
class BookingControllerTest {

    private static final String NO_SEATS_MESSAGE = "El viaje ya no tiene asientos disponibles.";
    private static final UUID PASSENGER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String VALID_BODY = """
            {"groupId":"11111111-1111-1111-1111-111111111111",
             "requestId":"22222222-2222-2222-2222-222222222222"}""";

    private final BookingService bookingService = mock(BookingService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BookingController(bookingService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createReturns202WithLocationAndRequestedBody() throws Exception {
        UUID bookingId = UUID.randomUUID();
        when(bookingService.createBooking(any(CreateBookingRequest.class), eq(PASSENGER_ID), eq("idem-key-1")))
                .thenReturn(new BookingResponse(bookingId, BookingStatus.REQUESTED));

        mockMvc.perform(post("/bookings")
                        .header("X-User-Id", PASSENGER_ID.toString())
                        .header("Idempotency-Key", "idem-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/bookings/" + bookingId))
                .andExpect(jsonPath("$.bookingId").value(bookingId.toString()))
                .andExpect(jsonPath("$.status").value("REQUESTED"));
    }

    @Test
    void createWithoutGroupIdIsA400AndNeverReachesTheService() throws Exception {
        mockMvc.perform(post("/bookings")
                        .header("X-User-Id", PASSENGER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"22222222-2222-2222-2222-222222222222\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(bookingService);
    }

    @Test
    void missingPassengerHeaderIsA400() throws Exception {
        when(bookingService.createBooking(any(), isNull(), any()))
                .thenThrow(new InvalidBookingRequestException("X-User-Id header is required"));

        mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("X-User-Id header is required"));
    }

    @Test
    void noSeatsIsA409WithTheExactMessage() throws Exception {
        when(bookingService.createBooking(any(), any(), any())).thenThrow(new NoSeatsAvailableException());

        mockMvc.perform(post("/bookings")
                        .header("X-User-Id", PASSENGER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(NO_SEATS_MESSAGE));
    }

    @Test
    void getExistingBookingReturns200WithUppercaseStatus() throws Exception {
        UUID bookingId = UUID.randomUUID();
        when(bookingService.findById(bookingId)).thenReturn(new BookingResponse(bookingId, BookingStatus.REQUESTED));

        mockMvc.perform(get("/bookings/" + bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(bookingId.toString()))
                .andExpect(jsonPath("$.status").value("REQUESTED"));
    }

    @Test
    void getUnknownBookingReturns404() throws Exception {
        UUID bookingId = UUID.randomUUID();
        when(bookingService.findById(bookingId)).thenThrow(new BookingNotFoundException(bookingId));

        mockMvc.perform(get("/bookings/" + bookingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Booking not found: " + bookingId));
    }

    @Test
    void cancelWithTheOwnerHeaderReturns200() throws Exception {
        UUID bookingId = UUID.randomUUID();
        BookingResponse cancelled = new BookingResponse(bookingId, BookingStatus.CANCELLED);
        when(bookingService.cancel(eq(bookingId), eq(PASSENGER_ID)))
                .thenReturn(new BookingService.CancellationResponse(cancelled, false));

        mockMvc.perform(post("/bookings/" + bookingId + "/cancel")
                        .header("X-User-Id", PASSENGER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.booking.bookingId").value(bookingId.toString()))
                .andExpect(jsonPath("$.booking.status").value("CANCELLED"))
                .andExpect(jsonPath("$.penaltyApplicable").value(false));
    }

    @Test
    void cancelWithoutXUserHeaderIsA400() throws Exception {
        UUID bookingId = UUID.randomUUID();
        when(bookingService.cancel(eq(bookingId), isNull()))
                .thenThrow(new InvalidBookingRequestException("X-User-Id header is required"));

        mockMvc.perform(post("/bookings/" + bookingId + "/cancel"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("X-User-Id header is required"));
    }

    @Test
    void cancelForAnotherPassengerIsA404() throws Exception {
        UUID bookingId = UUID.randomUUID();
        UUID strangerId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        when(bookingService.cancel(eq(bookingId), eq(strangerId)))
                .thenThrow(new BookingNotFoundException(bookingId));

        mockMvc.perform(post("/bookings/" + bookingId + "/cancel")
                        .header("X-User-Id", strangerId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
