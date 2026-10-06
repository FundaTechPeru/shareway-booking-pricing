package com.fundatechperu.shareway.bookingpricing.pricing.application.service;

import com.fundatechperu.shareway.bookingpricing.pricing.domain.exception.FareNotFoundException;
import com.fundatechperu.shareway.bookingpricing.pricing.domain.model.Fare;
import com.fundatechperu.shareway.bookingpricing.pricing.domain.model.FareRule;
import com.fundatechperu.shareway.bookingpricing.pricing.domain.repository.FareRepository;
import com.fundatechperu.shareway.bookingpricing.pricing.domain.repository.FareRuleRepository;
import com.fundatechperu.shareway.bookingpricing.pricing.domain.service.FareCalculationStrategy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class FareService {
    private final FareRuleRepository fareRuleRepository;
    private final FareRepository fareRepository;
    private final FareCalculationStrategy calculationStrategy;

    public FareService(FareRuleRepository fareRuleRepository, FareRepository fareRepository,
                       FareCalculationStrategy calculationStrategy) {
        this.fareRuleRepository = fareRuleRepository;
        this.fareRepository = fareRepository;
        this.calculationStrategy = calculationStrategy;
    }

    @Transactional
    public Fare recordFare(UUID groupId, String zone, LocalDate date, BigDecimal distanceKm, int passengers) {
        FareRule rule = fareRuleRepository.findActive(zone, date)
                .orElseThrow(() -> new FareNotFoundException("No active fare rule found"));
        FareCalculationStrategy.FareCalculation calculation =
                calculationStrategy.calculate(rule, distanceKm, passengers);
        var existing = fareRepository.findMatching(groupId, calculation.ruleVersion(),
                calculation.baseAmount().amount(), calculation.amountPerPassenger().amount(),
                calculation.platformFee().amount());
        if (existing.isPresent()) return existing.get();
        return fareRepository.save(new Fare(UUID.randomUUID(), groupId, rule.fareRuleId(), rule.ruleVersion(),
                calculation.baseAmount().amount(), calculation.amountPerPassenger().amount(),
                calculation.platformFee().amount(), LocalDateTime.now()));
    }

    @Transactional(readOnly = true)
    public Fare findCurrentByGroupId(UUID groupId) {
        return fareRepository.findCurrentByGroupId(groupId)
                .orElseThrow(() -> new FareNotFoundException("No fare recorded for group " + groupId));
    }
}
