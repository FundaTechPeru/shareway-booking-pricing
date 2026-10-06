package com.fundatechperu.shareway.bookingpricing.pricing.infrastructure.persistence.entity;

import com.fundatechperu.shareway.bookingpricing.pricing.domain.model.FareRuleStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "fare_rules")
public class FareRuleJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fare_rule_id", nullable = false)
    private Long fareRuleId;
    @Column(nullable = false, length = 120)
    private String zone;
    @Column(name = "base_distance_km", nullable = false, precision = 10, scale = 2)
    private BigDecimal baseDistanceKm;
    @Column(name = "base_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal basePrice;
    @Column(name = "price_per_km", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerKm;
    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;
    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FareRuleStatus status;
    @Column(name = "rule_version", nullable = false)
    private int ruleVersion;

    public Long getFareRuleId() { return fareRuleId; }
    public void setFareRuleId(Long value) { fareRuleId = value; }
    public String getZone() { return zone; }
    public void setZone(String value) { zone = value; }
    public BigDecimal getBaseDistanceKm() { return baseDistanceKm; }
    public void setBaseDistanceKm(BigDecimal value) { baseDistanceKm = value; }
    public BigDecimal getBasePrice() { return basePrice; }
    public void setBasePrice(BigDecimal value) { basePrice = value; }
    public BigDecimal getPricePerKm() { return pricePerKm; }
    public void setPricePerKm(BigDecimal value) { pricePerKm = value; }
    public LocalDate getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDate value) { validFrom = value; }
    public LocalDate getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDate value) { validUntil = value; }
    public FareRuleStatus getStatus() { return status; }
    public void setStatus(FareRuleStatus value) { status = value; }
    public int getRuleVersion() { return ruleVersion; }
    public void setRuleVersion(int value) { ruleVersion = value; }
}
