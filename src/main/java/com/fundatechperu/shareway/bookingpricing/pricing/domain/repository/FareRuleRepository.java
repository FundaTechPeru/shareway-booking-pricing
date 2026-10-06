package com.fundatechperu.shareway.bookingpricing.pricing.domain.repository;

import com.fundatechperu.shareway.bookingpricing.pricing.domain.model.FareRule;
import com.fundatechperu.shareway.bookingpricing.pricing.domain.model.FareRuleStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FareRuleRepository {
    FareRule save(FareRule fareRule);
    Optional<FareRule> findById(Long id);
    List<FareRule> findAll();
    List<FareRule> findByZone(String zone);
    Optional<FareRule> findActive(String zone, LocalDate date);
    boolean existsOverlappingActive(FareRule fareRule);
}
