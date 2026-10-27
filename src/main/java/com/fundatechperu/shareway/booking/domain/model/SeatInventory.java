package com.fundatechperu.shareway.booking.domain.model;

import com.fundatechperu.shareway.booking.domain.exception.NoSeatsAvailableException;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Seat inventory per trip group. Replaces the former TripGroup aggregate: the group
 * row is created when consuming the TripGroupOpened event and owns the seat count.
 */
public class SeatInventory {

    private final UUID groupId;
    private int capacity;
    private int availableSeats;
    private Long version;
    private LocalDateTime updatedAt;

    private SeatInventory(UUID groupId, int capacity, int availableSeats, Long version, LocalDateTime updatedAt) {
        if (capacity < 0) throw new IllegalArgumentException("capacity must not be negative");
        this.groupId = groupId;
        this.capacity = capacity;
        this.availableSeats = availableSeats;
        this.version = version;
        this.updatedAt = updatedAt;
    }

    public static SeatInventory create(UUID groupId, int capacity) {
        return new SeatInventory(groupId, capacity, capacity, 0L, LocalDateTime.now());
    }

    public static SeatInventory restore(UUID groupId, int capacity, int availableSeats, Long version,
                                        LocalDateTime updatedAt) {
        return new SeatInventory(groupId, capacity, availableSeats, version, updatedAt);
    }

    public void reserveSeat() {
        if (availableSeats <= 0) throw new NoSeatsAvailableException();
        availableSeats--;
        updatedAt = LocalDateTime.now();
    }

    public void releaseSeat() {
        if (availableSeats < capacity) {
            availableSeats++;
            updatedAt = LocalDateTime.now();
        }
    }

    public UUID getGroupId() { return groupId; }
    public int getCapacity() { return capacity; }
    public int getAvailableSeats() { return availableSeats; }
    public Long getVersion() { return version; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
