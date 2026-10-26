package com.fundatechperu.shareway.booking.pricing.application.service;

import com.fundatechperu.shareway.booking.pricing.domain.exception.FareNotFoundException;
import com.fundatechperu.shareway.booking.pricing.domain.exception.InvalidFareRuleException;
import com.fundatechperu.shareway.booking.pricing.domain.model.FareRule;
import com.fundatechperu.shareway.booking.pricing.domain.repository.FareRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;

@Service
public class FareRuleService {
    private final FareRuleRepository repository;
    public FareRuleService(FareRuleRepository repository) { this.repository = repository; }

    @Transactional
    public FareRule save(FareRule rule) {
        validate(rule);
        if (repository.existsOverlapping(rule)) throw new InvalidFareRuleException("Fare rule validity overlaps an existing rule");
        int version = rule.version() > 0 ? rule.version() : repository.nextVersion();
        return repository.save(new FareRule(rule.ruleId(), version, rule.baseAmount(), rule.amountPerKm(),
                rule.platformFeePct(), rule.validFrom(), rule.validTo()));
    }

    @Transactional(readOnly = true)
    public FareRule findById(Long id) {
        return repository.findById(id).orElseThrow(() -> new FareNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<FareRule> findAll() { return repository.findAll(); }

    @Transactional(readOnly = true)
    public FareRule findActive(LocalDate date) {
        return repository.findActive(date).orElseThrow(() -> new FareNotFoundException("No active fare rule found"));
    }

    @Transactional
    public FareRule update(Long id, FareRule candidate) {
        FareRule existing = findById(id);
        return save(new FareRule(id, existing.version(), candidate.baseAmount(), candidate.amountPerKm(),
                candidate.platformFeePct(), candidate.validFrom(), candidate.validTo()));
    }

    private void validate(FareRule rule) {
        if (rule.validFrom() == null || rule.validTo() == null)
            throw new InvalidFareRuleException("validFrom and validTo are required");
        if (rule.validFrom().isAfter(rule.validTo()))
            throw new InvalidFareRuleException("validFrom must not be after validTo");
    }
}
