package com.fundatechperu.shareway.bookingpricing.pricing.infrastructure.persistence.repository;

import com.fundatechperu.shareway.bookingpricing.pricing.domain.model.FareRule;
import com.fundatechperu.shareway.bookingpricing.pricing.domain.repository.FareRuleRepository;
import com.fundatechperu.shareway.bookingpricing.pricing.infrastructure.persistence.entity.FareRuleJpaEntity;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class FareRuleRepositoryAdapter implements FareRuleRepository {
    private final SpringDataFareRuleRepository repository;
    public FareRuleRepositoryAdapter(SpringDataFareRuleRepository repository) { this.repository = repository; }

    public FareRule save(FareRule rule) { return toDomain(repository.save(toEntity(rule))); }
    public Optional<FareRule> findById(Long id) { return repository.findById(id).map(this::toDomain); }
    public List<FareRule> findAll() { return repository.findAll().stream().map(this::toDomain).toList(); }
    public List<FareRule> findByZone(String zone) { return repository.findByZone(zone).stream().map(this::toDomain).toList(); }
    public Optional<FareRule> findActive(String zone, LocalDate date) {
        return repository.findFirstByZoneAndStatusAndValidFromLessThanEqualAndValidUntilGreaterThanEqualOrderByValidFromDesc(
                zone, com.fundatechperu.shareway.bookingpricing.pricing.domain.model.FareRuleStatus.ACTIVE, date, date)
                .map(this::toDomain);
    }
    public boolean existsOverlappingActive(FareRule rule) {
        return repository.findByZone(rule.zone()).stream().anyMatch(existing ->
                existing.getStatus() == com.fundatechperu.shareway.bookingpricing.pricing.domain.model.FareRuleStatus.ACTIVE
                        && rule.status() == com.fundatechperu.shareway.bookingpricing.pricing.domain.model.FareRuleStatus.ACTIVE
                        && !existing.getValidFrom().isAfter(rule.validUntil())
                        && !existing.getValidUntil().isBefore(rule.validFrom())
                        && !existing.getFareRuleId().equals(rule.fareRuleId()));
    }
    private FareRuleJpaEntity toEntity(FareRule rule) {
        FareRuleJpaEntity e = new FareRuleJpaEntity();
        e.setFareRuleId(rule.fareRuleId()); e.setZone(rule.zone()); e.setBaseDistanceKm(rule.baseDistanceKm());
        e.setBasePrice(rule.basePrice()); e.setPricePerKm(rule.pricePerKm()); e.setValidFrom(rule.validFrom());
        e.setValidUntil(rule.validUntil()); e.setStatus(rule.status()); e.setRuleVersion(rule.ruleVersion());
        return e;
    }
    private FareRule toDomain(FareRuleJpaEntity e) {
        return new FareRule(e.getFareRuleId(), e.getZone(), e.getBaseDistanceKm(), e.getBasePrice(),
                e.getPricePerKm(), e.getValidFrom(), e.getValidUntil(), e.getStatus(), e.getRuleVersion());
    }
}
