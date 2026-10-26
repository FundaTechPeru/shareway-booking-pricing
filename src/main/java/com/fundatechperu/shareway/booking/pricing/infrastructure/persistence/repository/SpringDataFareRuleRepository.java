package com.fundatechperu.shareway.booking.pricing.infrastructure.persistence.repository;

import com.fundatechperu.shareway.booking.pricing.infrastructure.persistence.entity.FareRuleJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SpringDataFareRuleRepository extends JpaRepository<FareRuleJpaEntity, Long> {

    Optional<FareRuleJpaEntity> findTopByOrderByVersionDesc();

    // A derived query consumes one argument per comparison (valid_from <= ? AND valid_to >= ?),
    // so the caller passes the same day as both bounds: the rule whose range includes that day.
    Optional<FareRuleJpaEntity> findFirstByValidFromLessThanEqualAndValidToGreaterThanEqualOrderByVersionDesc(
            LocalDate from, LocalDate to);

    List<FareRuleJpaEntity> findAllByOrderByValidFromDesc();

    List<FareRuleJpaEntity> findByValidFromLessThanEqualAndValidToGreaterThanEqual(LocalDate from, LocalDate to);
}
