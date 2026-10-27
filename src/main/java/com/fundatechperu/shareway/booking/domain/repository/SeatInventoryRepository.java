package com.fundatechperu.shareway.booking.domain.repository;

import com.fundatechperu.shareway.booking.domain.model.SeatInventory;

import java.util.Optional;
import java.util.UUID;

public interface SeatInventoryRepository {
    Optional<SeatInventory> findById(UUID groupId);

    /**
     * Same as {@link #findById(UUID)} but locking the row (SELECT ... FOR UPDATE): used by
     * the cancellation paths, where many transactions release seats of the same group and
     * would exhaust the optimistic lock of seat_inventories.version.
     */
    Optional<SeatInventory> findByIdForUpdate(UUID groupId);

    SeatInventory save(SeatInventory inventory);
}
