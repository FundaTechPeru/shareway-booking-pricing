package com.fundatechperu.shareway.bookingpricing.pricing.infrastructure.persistence.repository;

import com.fundatechperu.shareway.bookingpricing.pricing.infrastructure.persistence.entity.FareJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataFareRepository extends JpaRepository<FareJpaEntity, UUID> {
    Optional<FareJpaEntity> findFirstByGroupIdOrderByCreatedAtDesc(UUID groupId);
    Optional<FareJpaEntity> findFirstByGroupIdAndRuleVersionAndBaseAmountAndAmountPerPassengerAndPlatformFee(
            UUID groupId, int ruleVersion, java.math.BigDecimal baseAmount,
            java.math.BigDecimal amountPerPassenger, java.math.BigDecimal platformFee);
}
