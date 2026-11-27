package com.fundatechperu.shareway.booking.bdd;

import com.fundatechperu.shareway.booking.application.dto.request.CreateBookingRequest;
import com.fundatechperu.shareway.booking.application.dto.response.BookingResponse;
import com.fundatechperu.shareway.booking.application.handler.ProposalAcceptedHandler;
import com.fundatechperu.shareway.booking.application.service.BookingService;
import com.fundatechperu.shareway.booking.domain.exception.BookingCannotBeCancelledException;
import com.fundatechperu.shareway.booking.domain.model.BookingStatus;
import com.fundatechperu.shareway.booking.shared.events.ProposalAccepted;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class BookingStepDefinitions {

    private static final UUID GROUP_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired BookingService bookingService;
    @Autowired ProposalAcceptedHandler proposalAcceptedHandler;

    private UUID passengerId;
    private UUID bookingId;
    private BookingStatus lastStatus;
    private RuntimeException cancellationError;

    @Before
    public void reset() {
        jdbcTemplate.execute("TRUNCATE TABLE fares, penalties, booking_sagas, bookings, outbox_messages CASCADE");
        // The scenarios use one trip group with four free seats; the test creates it itself.
        jdbcTemplate.update("DELETE FROM seat_inventories WHERE group_id = ?", GROUP_ID);
        jdbcTemplate.update("INSERT INTO seat_inventories (group_id, capacity, available_seats) VALUES (?, 4, 4)",
                GROUP_ID);
        cancellationError = null;
    }

    @Given("a grouped booking exists for passenger {string}")
    public void groupedBooking(String passenger) {
        passengerId = UUID.fromString(passenger);
        bookingId = createBooking();
        jdbcTemplate.update("UPDATE bookings SET status='grouped' WHERE booking_id=?", bookingId);
    }

    @Given("a confirmed booking exists for passenger {string}")
    public void confirmedBooking(String passenger) {
        passengerId = UUID.fromString(passenger);
        bookingId = createBooking();
        jdbcTemplate.update("UPDATE bookings SET status='confirmed' WHERE booking_id=?", bookingId);
    }

    @Given("a requested booking exists for passenger {string}")
    public void requestedBooking(String passenger) {
        passengerId = UUID.fromString(passenger);
        bookingId = createBooking();
    }

    @Given("a completed booking exists for passenger {string}")
    public void completedBooking(String passenger) {
        passengerId = UUID.fromString(passenger);
        bookingId = createBooking();
        jdbcTemplate.update("UPDATE bookings SET status='completed' WHERE booking_id=?", bookingId);
    }

    @Given("no bookings exist for group {string}")
    public void noBookings(String group) {
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM bookings WHERE group_id=?",
                Integer.class, UUID.fromString(group)));
    }

    @When("the proposal for its group is accepted")
    public void acceptProposal() {
        proposalAcceptedHandler.onEvent(new ProposalAccepted(UUID.randomUUID(), Instant.now(), GROUP_ID,
                UUID.fromString("55555555-5555-5555-5555-555555555555")));
    }

    @When("the proposal for its group is accepted again")
    public void acceptProposalAgain() {
        acceptProposal();
    }

    @When("the passenger cancels the booking")
    public void cancel() {
        try {
            var response = bookingService.cancel(bookingId, passengerId);
            lastStatus = response.status();
        } catch (BookingCannotBeCancelledException exception) {
            cancellationError = exception;
        }
    }

    @When("the passenger cancels the booking again")
    public void cancelAgain() {
        cancel();
    }

    @Then("the booking status is {string}")
    @Then("the booking status remains {string}")
    public void statusIs(String status) {
        String actual = jdbcTemplate.queryForObject("SELECT status FROM bookings WHERE booking_id=?", String.class, bookingId);
        assertEquals(status.toLowerCase(), actual);
        lastStatus = BookingStatus.valueOf(status);
    }

    @Then("no booking is changed")
    public void noBookingChanged() {
        assertTrue(jdbcTemplate.queryForObject("SELECT count(*) FROM bookings", Integer.class) == 0);
    }

    @Then("cancellation is rejected with conflict")
    public void cancellationConflict() {
        assertNotNull(cancellationError);
        assertTrue(cancellationError.getMessage().contains("cannot be cancelled"));
    }

    private UUID createBooking() {
        CreateBookingRequest request = new CreateBookingRequest();
        request.setGroupId(GROUP_ID);
        request.setRequestId(UUID.randomUUID());
        BookingResponse response = bookingService.createBooking(request, passengerId, UUID.randomUUID().toString());
        return response.bookingId();
    }
}
