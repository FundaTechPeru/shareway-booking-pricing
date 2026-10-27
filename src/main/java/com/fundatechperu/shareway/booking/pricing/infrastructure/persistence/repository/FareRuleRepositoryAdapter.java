package com.fundatechperu.shareway.booking.pricing.infrastructure.persistence.repository;

import com.fundatechperu.shareway.booking.pricing.domain.model.FareRule;
import com.fundatechperu.shareway.booking.pricing.domain.repository.FareRuleRepository;
import com.fundatechperu.shareway.booking.pricing.infrastructure.persistence.entity.FareRuleJpaEntity;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class FareRuleRepositoryAdapter implements FareRuleRepository {
    private final SpringDataFareRuleRepository repository;
    public FareRuleRepositoryAdapter(SpringDataFareRuleRepository repository) { this.repository = repository; }

    public Optional<FareRule> findById(Long id) { return repository.findById(id).map(this::toDomain); }
    public List<FareRule> findAll() { return repository.findAllByOrderByValidFromDesc().stream().map(this::toDomain).toList(); }
    public Optional<FareRule> findActive(LocalDate date) {
        // valid rule on `date`: valid_from <= date AND valid_to >= date, highest version first
        return repository.findFirstByValidFromLessThanEqualAndValidToGreaterThanEqualOrderByVersionDesc(date, date)
                .map(this::toDomain);
    }
    private FareRule toDomain(FareRuleJpaEntity entity) {
        return new FareRule(entity.getRuleId(), entity.getVersion(), entity.getBaseAmount(),
                entity.getAmountPerKm(), entity.getPlatformFeePct(), entity.getValidFrom(), entity.getValidTo());
    }
}
