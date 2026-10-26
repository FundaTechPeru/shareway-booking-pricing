package com.fundatechperu.shareway.booking.pricing.application.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public class CreateFareRuleRequest {
    @NotNull @DecimalMin("0.0") private BigDecimal baseAmount;
    @NotNull @DecimalMin("0.0") private BigDecimal amountPerKm;
    @NotNull @DecimalMin("0.0") @DecimalMax("100.0") private BigDecimal platformFeePct;
    @NotNull private LocalDate validFrom;
    @NotNull private LocalDate validTo;
    public BigDecimal getBaseAmount() { return baseAmount; } public void setBaseAmount(BigDecimal v) { baseAmount = v; }
    public BigDecimal getAmountPerKm() { return amountPerKm; } public void setAmountPerKm(BigDecimal v) { amountPerKm = v; }
    public BigDecimal getPlatformFeePct() { return platformFeePct; } public void setPlatformFeePct(BigDecimal v) { platformFeePct = v; }
    public LocalDate getValidFrom() { return validFrom; } public void setValidFrom(LocalDate v) { validFrom = v; }
    public LocalDate getValidTo() { return validTo; } public void setValidTo(LocalDate v) { validTo = v; }
}
