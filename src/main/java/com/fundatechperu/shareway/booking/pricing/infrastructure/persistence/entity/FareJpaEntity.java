package com.fundatechperu.shareway.booking.pricing.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "fares", uniqueConstraints = @UniqueConstraint(columnNames = {"booking_id", "fare_type"}))
public class FareJpaEntity {

    @Id
    @Column(name = "fare_id", nullable = false)
    private UUID fareId;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "rule_id", nullable = false)
    private Long ruleId;

    /** estimated | final (CHECK in V1). */
    @Column(name = "fare_type", nullable = false, length = 20)
    private String fareType;

    @Column(name = "distance_km", nullable = false, precision = 10, scale = 2)
    private BigDecimal distanceKm;

    @Column(name = "grouped_passengers", nullable = false)
    private int groupedPassengers;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    public FareJpaEntity() {
    }

    public UUID getFareId() { return fareId; }
    public void setFareId(UUID fareId) { this.fareId = fareId; }
    public UUID getBookingId() { return bookingId; }
    public void setBookingId(UUID bookingId) { this.bookingId = bookingId; }
    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    public String getFareType() { return fareType; }
    public void setFareType(String fareType) { this.fareType = fareType; }
    public BigDecimal getDistanceKm() { return distanceKm; }
    public void setDistanceKm(BigDecimal distanceKm) { this.distanceKm = distanceKm; }
    public int getGroupedPassengers() { return groupedPassengers; }
    public void setGroupedPassengers(int groupedPassengers) { this.groupedPassengers = groupedPassengers; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
