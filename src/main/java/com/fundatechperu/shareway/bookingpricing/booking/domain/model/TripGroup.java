package com.fundatechperu.shareway.bookingpricing.booking.domain.model;

import com.fundatechperu.shareway.bookingpricing.booking.domain.exception.NoSeatsAvailableException;

import java.time.LocalDateTime;
import java.util.UUID;

public class TripGroup {
    private final UUID groupId;
    private final int capacity;
    private final int minPassengers;
    private int availableSeats;
    private final String status;
    private Long version;
    private final LocalDateTime createdAt;

    private TripGroup(UUID groupId, int capacity, int minPassengers, int availableSeats,
                      String status, Long version, LocalDateTime createdAt) {
        this.groupId = groupId;
        this.capacity = capacity;
        this.minPassengers = minPassengers;
        this.availableSeats = availableSeats;
        this.status = status;
        this.version = version;
        this.createdAt = createdAt;
    }

    public static TripGroup restore(UUID groupId, int capacity, int minPassengers, int availableSeats,
                                    String status, Long version, LocalDateTime createdAt) {
        return new TripGroup(groupId, capacity, minPassengers, availableSeats, status, version, createdAt);
    }

    public void reserveSeat() {
        if (availableSeats == 0) {
            throw new NoSeatsAvailableException();
        }
        availableSeats--;
    }

    public void releaseSeat() {
        if (availableSeats < capacity) {
            availableSeats++;
        }
    }

    public UUID getGroupId() { return groupId; }
    public int getCapacity() { return capacity; }
    public int getMinPassengers() { return minPassengers; }
    public int getAvailableSeats() { return availableSeats; }
    public String getStatus() { return status; }
    public Long getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
