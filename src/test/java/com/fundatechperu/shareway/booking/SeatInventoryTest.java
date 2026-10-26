package com.fundatechperu.shareway.booking;

import com.fundatechperu.shareway.booking.domain.exception.NoSeatsAvailableException;
import com.fundatechperu.shareway.booking.domain.model.SeatInventory;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SeatInventoryTest {

    @Test
    void createStartsWithEverySeatAvailable() {
        SeatInventory inventory = SeatInventory.create(UUID.randomUUID(), 4);
        assertEquals(4, inventory.getAvailableSeats());
        assertEquals(4, inventory.getCapacity());
        assertEquals(0L, inventory.getVersion());
    }

    @Test
    void reserveSeatTakesOneSeat() {
        SeatInventory inventory = SeatInventory.create(UUID.randomUUID(), 4);
        inventory.reserveSeat();
        inventory.reserveSeat();
        assertEquals(2, inventory.getAvailableSeats());
    }

    @Test
    void reserveSeatThrowsWhenNoSeatsAreLeft() {
        SeatInventory inventory = SeatInventory.restore(UUID.randomUUID(), 4, 0, 0L, null);
        assertThrows(NoSeatsAvailableException.class, inventory::reserveSeat);
        assertEquals(0, inventory.getAvailableSeats());
    }

    @Test
    void releaseSeatGivesTheSeatBack() {
        SeatInventory inventory = SeatInventory.restore(UUID.randomUUID(), 4, 1, 0L, null);
        inventory.releaseSeat();
        assertEquals(2, inventory.getAvailableSeats());
    }

    @Test
    void releaseSeatNeverExceedsCapacity() {
        SeatInventory inventory = SeatInventory.create(UUID.randomUUID(), 2);
        inventory.releaseSeat();
        assertEquals(2, inventory.getAvailableSeats());
    }
}
