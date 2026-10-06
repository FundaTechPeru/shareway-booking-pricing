package com.fundatechperu.shareway.bookingpricing.pricing.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "fares")
public class FareJpaEntity {
    @Id @Column(name = "fare_id", nullable = false) private UUID fareId;
    @Column(name = "group_id", nullable = false) private UUID groupId;
    @Column(name = "fare_rule_id", nullable = false) private Long fareRuleId;
    @Column(name = "rule_version", nullable = false) private int ruleVersion;
    @Column(name = "base_amount", nullable = false, precision = 10, scale = 2) private BigDecimal baseAmount;
    @Column(name = "amount_per_passenger", nullable = false, precision = 10, scale = 2) private BigDecimal amountPerPassenger;
    @Column(name = "platform_fee", nullable = false, precision = 10, scale = 2) private BigDecimal platformFee;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    public UUID getFareId() { return fareId; } public void setFareId(UUID v) { fareId = v; }
    public UUID getGroupId() { return groupId; } public void setGroupId(UUID v) { groupId = v; }
    public Long getFareRuleId() { return fareRuleId; } public void setFareRuleId(Long v) { fareRuleId = v; }
    public int getRuleVersion() { return ruleVersion; } public void setRuleVersion(int v) { ruleVersion = v; }
    public BigDecimal getBaseAmount() { return baseAmount; } public void setBaseAmount(BigDecimal v) { baseAmount = v; }
    public BigDecimal getAmountPerPassenger() { return amountPerPassenger; } public void setAmountPerPassenger(BigDecimal v) { amountPerPassenger = v; }
    public BigDecimal getPlatformFee() { return platformFee; } public void setPlatformFee(BigDecimal v) { platformFee = v; }
    public LocalDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(LocalDateTime v) { createdAt = v; }
}
