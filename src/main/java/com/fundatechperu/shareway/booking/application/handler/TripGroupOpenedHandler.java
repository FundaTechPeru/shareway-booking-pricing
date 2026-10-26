package com.fundatechperu.shareway.booking.application.handler;

import com.fundatechperu.shareway.booking.domain.model.SeatInventory;
import com.fundatechperu.shareway.booking.domain.repository.SeatInventoryRepository;
import com.fundatechperu.shareway.booking.shared.events.EventHandler;
import com.fundatechperu.shareway.booking.shared.events.TripGroupOpened;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * TripGroupOpened: creates the seat_inventory owned by this service (group_id is a plain
 * UUID, no foreign keys toward Driver Operations & Routing). Re-delivery is a no-op.
 */
@Service
public class TripGroupOpenedHandler implements EventHandler {

    private final SeatInventoryRepository seatInventoryRepository;

    public TripGroupOpenedHandler(SeatInventoryRepository seatInventoryRepository) {
        this.seatInventoryRepository = seatInventoryRepository;
    }

    @Override
    public boolean supports(Object event) {
        return event instanceof TripGroupOpened;
    }

    @Override
    @Transactional
    public void onEvent(Object event) {
        TripGroupOpened opened = (TripGroupOpened) event;
        if (seatInventoryRepository.findById(opened.groupId()).isEmpty()) {
            seatInventoryRepository.save(SeatInventory.create(opened.groupId(), opened.capacity()));
        }
    }
}
