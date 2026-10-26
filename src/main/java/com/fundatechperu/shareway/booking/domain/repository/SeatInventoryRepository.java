package com.fundatechperu.shareway.booking.domain.repository;

import com.fundatechperu.shareway.booking.domain.model.SeatInventory;

import java.util.Optional;
import java.util.UUID;

public interface SeatInventoryRepository {
    Optional<SeatInventory> findById(UUID groupId);
    SeatInventory save(SeatInventory inventory);
}
