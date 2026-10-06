package com.fundatechperu.shareway.bookingpricing.pricing.application.service;

import com.fundatechperu.shareway.bookingpricing.pricing.domain.exception.FareNotFoundException;
import com.fundatechperu.shareway.bookingpricing.pricing.domain.exception.InvalidFareRuleException;
import com.fundatechperu.shareway.bookingpricing.pricing.domain.model.FareRule;
import com.fundatechperu.shareway.bookingpricing.pricing.domain.repository.FareRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;

@Service
public class FareRuleService {
    private final FareRuleRepository repository;
    public FareRuleService(FareRuleRepository repository) { this.repository = repository; }
    @Transactional public FareRule save(FareRule rule) {
        validate(rule);
        if (repository.existsOverlappingActive(rule)) throw new InvalidFareRuleException("Active fare rule validity overlaps");
        return repository.save(rule);
    }
    @Transactional(readOnly = true) public FareRule findById(Long id) {
        return repository.findById(id).orElseThrow(() -> new FareNotFoundException(id));
    }
    @Transactional(readOnly = true) public List<FareRule> findAll() { return repository.findAll(); }
    @Transactional(readOnly = true) public List<FareRule> findByZone(String zone) { return repository.findByZone(zone); }
    @Transactional(readOnly = true) public FareRule findActive(String zone, LocalDate date) {
        return repository.findActive(zone, date).orElseThrow(() -> new FareNotFoundException("No active fare rule found"));
    }
    @Transactional public FareRule update(Long id, FareRule candidate) {
        findById(id);
        return save(new FareRule(id, candidate.zone(), candidate.baseDistanceKm(), candidate.basePrice(),
                candidate.pricePerKm(), candidate.validFrom(), candidate.validUntil(), candidate.status(),
                candidate.ruleVersion()));
    }
    private void validate(FareRule rule) {
        if (rule.validFrom().isAfter(rule.validUntil())) throw new InvalidFareRuleException("validFrom must not be after validUntil");
    }
}
