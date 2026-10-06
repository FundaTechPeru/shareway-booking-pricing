package com.fundatechperu.shareway.bookingpricing.booking.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "trip_groups")
public class TripGroupJpaEntity {
    @Id
    @Column(name = "group_id", nullable = false)
    private UUID groupId;
    @Column(nullable = false)
    private int capacity;
    @Column(name = "min_passengers", nullable = false)
    private int minPassengers;
    @Column(name = "available_seats", nullable = false)
    private int availableSeats;
    @Column(nullable = false)
    private String status;
    @Version
    @Column(nullable = false)
    private Long version;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public UUID getGroupId() { return groupId; }
    public void setGroupId(UUID groupId) { this.groupId = groupId; }
    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public int getMinPassengers() { return minPassengers; }
    public void setMinPassengers(int minPassengers) { this.minPassengers = minPassengers; }
    public int getAvailableSeats() { return availableSeats; }
    public void setAvailableSeats(int availableSeats) { this.availableSeats = availableSeats; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
