package com.fundatechperu.shareway.bookingpricing.pricing.application.dto.request;

import com.fundatechperu.shareway.bookingpricing.pricing.domain.model.FareRuleStatus;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public class CreateFareRuleRequest {
    @NotBlank @Size(max = 120) private String zone;
    @NotNull @DecimalMin("0.0") private BigDecimal baseDistanceKm;
    @NotNull @DecimalMin("0.0") private BigDecimal basePrice;
    @NotNull @DecimalMin("0.0") private BigDecimal pricePerKm;
    @NotNull private LocalDate validFrom;
    @NotNull private LocalDate validUntil;
    @NotNull private FareRuleStatus status;
    public String getZone() { return zone; } public void setZone(String v) { zone = v; }
    public BigDecimal getBaseDistanceKm() { return baseDistanceKm; } public void setBaseDistanceKm(BigDecimal v) { baseDistanceKm = v; }
    public BigDecimal getBasePrice() { return basePrice; } public void setBasePrice(BigDecimal v) { basePrice = v; }
    public BigDecimal getPricePerKm() { return pricePerKm; } public void setPricePerKm(BigDecimal v) { pricePerKm = v; }
    public LocalDate getValidFrom() { return validFrom; } public void setValidFrom(LocalDate v) { validFrom = v; }
    public LocalDate getValidUntil() { return validUntil; } public void setValidUntil(LocalDate v) { validUntil = v; }
    public FareRuleStatus getStatus() { return status; } public void setStatus(FareRuleStatus v) { status = v; }
}
