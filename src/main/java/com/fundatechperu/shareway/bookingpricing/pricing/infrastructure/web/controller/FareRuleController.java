package com.fundatechperu.shareway.bookingpricing.pricing.infrastructure.web.controller;

import com.fundatechperu.shareway.bookingpricing.pricing.application.dto.request.CreateFareRuleRequest;
import com.fundatechperu.shareway.bookingpricing.pricing.application.dto.request.UpdateFareRuleRequest;
import com.fundatechperu.shareway.bookingpricing.pricing.application.dto.response.FareRuleResponse;
import com.fundatechperu.shareway.bookingpricing.pricing.application.service.FareRuleService;
import com.fundatechperu.shareway.bookingpricing.pricing.domain.model.FareRule;
import com.fundatechperu.shareway.bookingpricing.pricing.domain.service.FareCalculationStrategy;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/pricing")
public class FareRuleController {
    private final FareRuleService service;
    private final FareCalculationStrategy strategy;

    public FareRuleController(FareRuleService service, FareCalculationStrategy strategy) {
        this.service = service;
        this.strategy = strategy;
    }

    @PostMapping("/fare-rules")
    public ResponseEntity<FareRuleResponse> create(@Valid @RequestBody CreateFareRuleRequest request) {
        FareRuleResponse response = FareRuleResponse.from(service.save(toDomain(request, null)));
        return ResponseEntity.created(URI.create("/api/v1/pricing/fare-rules/" + response.fareRuleId())).body(response);
    }

    @GetMapping("/fare-rules/{id}")
    public ResponseEntity<FareRuleResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(FareRuleResponse.from(service.findById(id)));
    }

    @GetMapping("/fare-rules")
    public List<FareRuleResponse> find(@RequestParam(required = false) String zone) {
        return (zone == null ? service.findAll() : service.findByZone(zone)).stream()
                .map(FareRuleResponse::from).toList();
    }

    @PutMapping("/fare-rules/{id}")
    public FareRuleResponse update(@PathVariable Long id, @Valid @RequestBody UpdateFareRuleRequest request) {
        return FareRuleResponse.from(service.update(id, toDomain(request, id)));
    }

    @GetMapping("/fares/estimate")
    public EstimateResponse estimate(@RequestParam String zone, @RequestParam LocalDate date,
                                     @RequestParam BigDecimal distanceKm, @RequestParam int passengers) {
        FareCalculationStrategy.FareCalculation result =
                strategy.calculate(service.findActive(zone, date), distanceKm, passengers);
        return new EstimateResponse(result.baseAmount().amount(), result.amountPerPassenger().amount(),
                result.platformFee().amount(), result.ruleVersion());
    }

    private FareRule toDomain(CreateFareRuleRequest request, Long id) {
        return new FareRule(id, request.getZone(), request.getBaseDistanceKm(), request.getBasePrice(),
                request.getPricePerKm(), request.getValidFrom(), request.getValidUntil(), request.getStatus(), 1);
    }

    public record EstimateResponse(BigDecimal baseAmount, BigDecimal amountPerPassenger,
                                   BigDecimal platformFee, int ruleVersion) {
    }
}
