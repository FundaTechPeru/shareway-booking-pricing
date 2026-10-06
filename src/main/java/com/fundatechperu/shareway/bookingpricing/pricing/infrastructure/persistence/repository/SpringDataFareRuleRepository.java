package com.fundatechperu.shareway.bookingpricing.pricing.infrastructure.persistence.repository;

import com.fundatechperu.shareway.bookingpricing.pricing.domain.model.FareRuleStatus;
import com.fundatechperu.shareway.bookingpricing.pricing.infrastructure.persistence.entity.FareRuleJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SpringDataFareRuleRepository extends JpaRepository<FareRuleJpaEntity, Long> {
    List<FareRuleJpaEntity> findByZone(String zone);
    Optional<FareRuleJpaEntity> findFirstByZoneAndStatusAndValidFromLessThanEqualAndValidUntilGreaterThanEqualOrderByValidFromDesc(
            String zone, FareRuleStatus status, LocalDate dateFrom, LocalDate dateUntil);
    boolean existsByZoneAndStatusAndValidFromLessThanEqualAndValidUntilGreaterThanEqualAndValidFromLessThanEqualAndValidUntilGreaterThanEqual(
            String zone, FareRuleStatus status, LocalDate existingFrom, LocalDate existingUntil,
            LocalDate candidateFrom, LocalDate candidateUntil);
}
