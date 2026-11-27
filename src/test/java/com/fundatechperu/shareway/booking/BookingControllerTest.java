package com.fundatechperu.shareway.booking;

import com.fundatechperu.shareway.booking.application.dto.request.CreateBookingRequest;
import com.fundatechperu.shareway.booking.application.dto.response.BookingResponse;
import com.fundatechperu.shareway.booking.application.dto.response.CancellationResponse;
import com.fundatechperu.shareway.booking.application.service.BookingService;
import com.fundatechperu.shareway.booking.domain.exception.BookingCannotBeCancelledException;
import com.fundatechperu.shareway.booking.domain.exception.BookingNotFoundException;
import com.fundatechperu.shareway.booking.domain.exception.NoSeatsAvailableException;
import com.fundatechperu.shareway.booking.domain.model.BookingStatus;
import com.fundatechperu.shareway.booking.infrastructure.security.AuthenticatedUser;
import com.fundatechperu.shareway.booking.infrastructure.security.UserRole;
import com.fundatechperu.shareway.booking.infrastructure.web.GlobalExceptionHandler;
import com.fundatechperu.shareway.booking.interfaces.BookingController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HTTP contract of the booking endpoints (the caller comes from the authenticated JWT
 * principal; the security rules themselves are covered by BookingIntegrationTest):
 * 202 + Location, 400, 404 and the 409 body with "The trip has no seats available."
 */
class BookingControllerTest {

    private static final String NO_SEATS_MESSAGE = "The trip has no seats available.";
    private static final UUID PASSENGER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID GROUP_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final String VALID_BODY = """
            {"groupId":"44444444-4444-4444-4444-444444444444",
             "requestId":"22222222-2222-2222-2222-222222222222"}""";

    private final BookingService bookingService = mock(BookingService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BookingController(bookingService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        authenticateAs(PASSENGER_ID, UserRole.PASSENGER);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateAs(UUID userId, UserRole role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(userId, role), null, List.of()));
    }

    @Test
    void createReturns202WithLocationAndTheFullBody() throws Exception {
        UUID bookingId = UUID.randomUUID();
        UUID requestId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        when(bookingService.createBooking(any(CreateBookingRequest.class), eq(PASSENGER_ID), eq("idem-key-1")))
                .thenReturn(new BookingResponse(bookingId, GROUP_ID, requestId, BookingStatus.REQUESTED,
                        new BigDecimal("21.34"), "PEN", null, null));

        mockMvc.perform(post("/api/v1/bookings")
                        .header("Idempotency-Key", "idem-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/v1/bookings/" + bookingId))
                .andExpect(jsonPath("$.bookingId").value(bookingId.toString()))
                .andExpect(jsonPath("$.groupId").value(GROUP_ID.toString()))
                .andExpect(jsonPath("$.requestId").value(requestId.toString()))
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.estimatedAmount").value(21.34))
                .andExpect(jsonPath("$.currency").value("PEN"))
                .andExpect(jsonPath("$.boardingPin").doesNotExist())
                .andExpect(jsonPath("$.qrToken").doesNotExist());
    }

    @Test
    void theOptionalPaymentMethodReachesTheService() throws Exception {
        UUID paymentMethodId = UUID.randomUUID();
        when(bookingService.createBooking(any(), any(), any()))
                .thenReturn(new BookingResponse(UUID.randomUUID(), BookingStatus.REQUESTED));

        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupId\":\"" + GROUP_ID + "\",\"requestId\":\"" + UUID.randomUUID()
                                + "\",\"paymentMethodId\":\"" + paymentMethodId + "\"}"))
                .andExpect(status().isAccepted());

        ArgumentCaptor<CreateBookingRequest> request = ArgumentCaptor.forClass(CreateBookingRequest.class);
        verify(bookingService).createBooking(request.capture(), eq(PASSENGER_ID), any());
        assertEquals(paymentMethodId, request.getValue().getPaymentMethodId());
    }

    @Test
    void createWithoutGroupIdIsA400AndNeverReachesTheService() throws Exception {
        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"22222222-2222-2222-2222-222222222222\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(bookingService);
    }

    @Test
    void noSeatsIsA409WithTheExactMessage() throws Exception {
        when(bookingService.createBooking(any(), any(), any())).thenThrow(new NoSeatsAvailableException());

        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(NO_SEATS_MESSAGE));
    }

    @Test
    void theOwnerGetsTheConfirmedBookingWithItsBoardingCodes() throws Exception {
        UUID bookingId = UUID.randomUUID();
        when(bookingService.findById(bookingId, PASSENGER_ID, false)).thenReturn(new BookingResponse(bookingId,
                GROUP_ID, UUID.randomUUID(), BookingStatus.CONFIRMED, new BigDecimal("21.34"), "PEN",
                "004271", "9f86d081884c7d659a2feaa0c55ad015"));

        mockMvc.perform(get("/api/v1/bookings/" + bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(bookingId.toString()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.boardingPin").value("004271"))
                .andExpect(jsonPath("$.qrToken").value("9f86d081884c7d659a2feaa0c55ad015"));
    }

    @Test
    void anAdminQueryCarriesTheAdminFlag() throws Exception {
        UUID adminId = UUID.randomUUID();
        authenticateAs(adminId, UserRole.ADMIN);
        UUID bookingId = UUID.randomUUID();
        when(bookingService.findById(bookingId, adminId, true))
                .thenReturn(new BookingResponse(bookingId, BookingStatus.REQUESTED));
        when(bookingService.findAll(adminId, true)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/bookings/" + bookingId)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/bookings")).andExpect(status().isOk()).andExpect(jsonPath("$").isArray());

        verify(bookingService).findById(bookingId, adminId, true);
        verify(bookingService).findAll(adminId, true);
    }

    @Test
    void listReturnsThePassengerBookings() throws Exception {
        UUID bookingId = UUID.randomUUID();
        when(bookingService.findAll(PASSENGER_ID, false))
                .thenReturn(List.of(new BookingResponse(bookingId, BookingStatus.REQUESTED)));

        mockMvc.perform(get("/api/v1/bookings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bookingId").value(bookingId.toString()));
    }

    @Test
    void getUnknownBookingReturns404() throws Exception {
        UUID bookingId = UUID.randomUUID();
        when(bookingService.findById(bookingId, PASSENGER_ID, false)).thenThrow(new BookingNotFoundException(bookingId));

        mockMvc.perform(get("/api/v1/bookings/" + bookingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Booking not found: " + bookingId));
    }

    /** Uniform error body: {timestamp, status, error, message, path} on every failure. */
    @Test
    void everyErrorCarriesTheUniformBody() throws Exception {
        UUID bookingId = UUID.randomUUID();
        when(bookingService.findById(bookingId, PASSENGER_ID, false)).thenThrow(new BookingNotFoundException(bookingId));

        mockMvc.perform(get("/api/v1/bookings/" + bookingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Booking not found: " + bookingId))
                .andExpect(jsonPath("$.path").value("/api/v1/bookings/" + bookingId));
    }

    @Test
    void cancelByTheOwnerReturns200WithoutPenalty() throws Exception {
        UUID bookingId = UUID.randomUUID();
        when(bookingService.cancel(eq(bookingId), eq(PASSENGER_ID)))
                .thenReturn(new CancellationResponse(bookingId, BookingStatus.CANCELLED, null, null));

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(bookingId.toString()))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.penaltyAmount").isEmpty())
                .andExpect(jsonPath("$.currency").isEmpty());
    }

    @Test
    void lateCancelReportsThePenaltyAmountAndCurrency() throws Exception {
        UUID bookingId = UUID.randomUUID();
        when(bookingService.cancel(eq(bookingId), eq(PASSENGER_ID)))
                .thenReturn(new CancellationResponse(bookingId, BookingStatus.CANCELLED,
                        new BigDecimal("4.27"), "PEN"));

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.penaltyAmount").value(4.27))
                .andExpect(jsonPath("$.currency").value("PEN"));
    }

    @Test
    void cancellingABoardedBookingIsA409() throws Exception {
        UUID bookingId = UUID.randomUUID();
        when(bookingService.cancel(eq(bookingId), eq(PASSENGER_ID)))
                .thenThrow(new BookingCannotBeCancelledException(
                        "The booking cannot be cancelled from its current status: BOARDED"));

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message")
                        .value("The booking cannot be cancelled from its current status: BOARDED"));
    }

    @Test
    void cancelForAnotherPassengerIsA404() throws Exception {
        UUID bookingId = UUID.randomUUID();
        UUID strangerId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        authenticateAs(strangerId, UserRole.PASSENGER);
        when(bookingService.cancel(eq(bookingId), eq(strangerId)))
                .thenThrow(new BookingNotFoundException(bookingId));

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/cancel"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
