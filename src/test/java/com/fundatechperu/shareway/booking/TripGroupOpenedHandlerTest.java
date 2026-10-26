package com.fundatechperu.shareway.booking;

import com.fundatechperu.shareway.booking.application.handler.TripGroupOpenedHandler;
import com.fundatechperu.shareway.booking.domain.model.SeatInventory;
import com.fundatechperu.shareway.booking.domain.repository.SeatInventoryRepository;
import com.fundatechperu.shareway.booking.shared.events.TripGroupOpened;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TripGroupOpened handler: creates the seat_inventory, once.
 */
class TripGroupOpenedHandlerTest {

    private static final UUID GROUP_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final SeatInventoryRepository seatInventoryRepository = mock(SeatInventoryRepository.class);
    private final TripGroupOpenedHandler handler = new TripGroupOpenedHandler(seatInventoryRepository);

    private TripGroupOpened event(int capacity) {
        return new TripGroupOpened(UUID.randomUUID(), Instant.now(), GROUP_ID, capacity);
    }

    @Test
    void createsTheSeatInventoryWithFullCapacity() {
        when(seatInventoryRepository.findById(GROUP_ID)).thenReturn(Optional.empty());

        TripGroupOpened event = event(4);
        assertTrue(handler.supports(event));
        handler.onEvent(event);

        ArgumentCaptor<SeatInventory> captor = ArgumentCaptor.forClass(SeatInventory.class);
        verify(seatInventoryRepository).save(captor.capture());
        SeatInventory saved = captor.getValue();
        assertEquals(GROUP_ID, saved.getGroupId());
        assertEquals(4, saved.getCapacity());
        assertEquals(4, saved.getAvailableSeats());
        assertEquals(0L, saved.getVersion());
    }

    @Test
    void redeliveryDoesNotCreateOrTouchAnExistingInventory() {
        SeatInventory existing = SeatInventory.restore(GROUP_ID, 4, 2, 3L, null);
        when(seatInventoryRepository.findById(GROUP_ID)).thenReturn(Optional.of(existing));

        handler.onEvent(event(4));

        verify(seatInventoryRepository, never()).save(any());
        assertEquals(2, existing.getAvailableSeats(), "held seats must not be reset");
        assertEquals(4, existing.getCapacity());
    }
}
