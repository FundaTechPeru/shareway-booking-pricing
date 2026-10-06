package com.fundatechperu.shareway.bookingpricing.pricing.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity @Table(name = "driver_settlements")
public class DriverSettlementJpaEntity {
    @Id @Column(name = "settlement_id", nullable = false) private UUID settlementId;
    @Column(name = "trip_id", nullable = false) private UUID tripId;
    @Column(name = "driver_id", nullable = false) private UUID driverId;
    @Column(name = "gross_amount", nullable = false, precision = 10, scale = 2) private BigDecimal grossAmount;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal commission;
    @Column(name = "net_amount", nullable = false, precision = 10, scale = 2) private BigDecimal netAmount;
    @Column(nullable = false, length = 20) private String status;
    public UUID getSettlementId() { return settlementId; } public void setSettlementId(UUID v) { settlementId = v; }
    public UUID getTripId() { return tripId; } public void setTripId(UUID v) { tripId = v; }
    public UUID getDriverId() { return driverId; } public void setDriverId(UUID v) { driverId = v; }
    public BigDecimal getGrossAmount() { return grossAmount; } public void setGrossAmount(BigDecimal v) { grossAmount = v; }
    public BigDecimal getCommission() { return commission; } public void setCommission(BigDecimal v) { commission = v; }
    public BigDecimal getNetAmount() { return netAmount; } public void setNetAmount(BigDecimal v) { netAmount = v; }
    public String getStatus() { return status; } public void setStatus(String v) { status = v; }
}
