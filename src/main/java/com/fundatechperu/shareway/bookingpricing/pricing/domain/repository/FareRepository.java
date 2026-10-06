package com.fundatechperu.shareway.bookingpricing.pricing.domain.repository;

import com.fundatechperu.shareway.bookingpricing.pricing.domain.model.Fare;
import java.util.Optional;
import java.util.UUID;

public interface FareRepository {
    Fare save(Fare fare);
    Optional<Fare> findCurrentByGroupId(UUID groupId);
    Optional<Fare> findMatching(UUID groupId, int ruleVersion, java.math.BigDecimal baseAmount,
                                java.math.BigDecimal amountPerPassenger, java.math.BigDecimal platformFee);
}
