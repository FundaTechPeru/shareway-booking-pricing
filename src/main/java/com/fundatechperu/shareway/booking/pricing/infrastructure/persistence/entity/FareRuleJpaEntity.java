package com.fundatechperu.shareway.booking.pricing.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "fare_rules")
public class FareRuleJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rule_id", nullable = false)
    private Long ruleId;

    @Column(nullable = false, unique = true)
    private int version;

    @Column(name = "base_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal baseAmount;

    @Column(name = "amount_per_km", nullable = false, precision = 10, scale = 2)
    private BigDecimal amountPerKm;

    @Column(name = "platform_fee_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal platformFeePct;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to", nullable = false)
    private LocalDate validTo;

    public FareRuleJpaEntity() {
    }

    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public BigDecimal getBaseAmount() { return baseAmount; }
    public void setBaseAmount(BigDecimal baseAmount) { this.baseAmount = baseAmount; }
    public BigDecimal getAmountPerKm() { return amountPerKm; }
    public void setAmountPerKm(BigDecimal amountPerKm) { this.amountPerKm = amountPerKm; }
    public BigDecimal getPlatformFeePct() { return platformFeePct; }
    public void setPlatformFeePct(BigDecimal platformFeePct) { this.platformFeePct = platformFeePct; }
    public LocalDate getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDate validFrom) { this.validFrom = validFrom; }
    public LocalDate getValidTo() { return validTo; }
    public void setValidTo(LocalDate validTo) { this.validTo = validTo; }
}
