package com.fundatechperu.shareway.bookingpricing.pricing.infrastructure.persistence.repository;

import com.fundatechperu.shareway.bookingpricing.pricing.domain.model.Fare;
import com.fundatechperu.shareway.bookingpricing.pricing.domain.repository.FareRepository;
import com.fundatechperu.shareway.bookingpricing.pricing.infrastructure.persistence.entity.FareJpaEntity;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public class FareRepositoryAdapter implements FareRepository {
    private final SpringDataFareRepository repository;
    public FareRepositoryAdapter(SpringDataFareRepository repository) { this.repository = repository; }
    @Override public Fare save(Fare fare) { return toDomain(repository.save(toEntity(fare))); }
    @Override public Optional<Fare> findCurrentByGroupId(UUID groupId) {
        return repository.findFirstByGroupIdOrderByCreatedAtDesc(groupId).map(this::toDomain);
    }
    @Override public Optional<Fare> findMatching(UUID groupId, int ruleVersion, java.math.BigDecimal baseAmount,
                                                   java.math.BigDecimal amountPerPassenger, java.math.BigDecimal platformFee) {
        return repository.findFirstByGroupIdAndRuleVersionAndBaseAmountAndAmountPerPassengerAndPlatformFee(
                groupId, ruleVersion, baseAmount, amountPerPassenger, platformFee).map(this::toDomain);
    }
    private FareJpaEntity toEntity(Fare fare) {
        FareJpaEntity entity = new FareJpaEntity();
        entity.setFareId(fare.fareId()); entity.setGroupId(fare.groupId());
        entity.setFareRuleId(fare.fareRuleId()); entity.setRuleVersion(fare.ruleVersion());
        entity.setBaseAmount(fare.baseAmount()); entity.setAmountPerPassenger(fare.amountPerPassenger());
        entity.setPlatformFee(fare.platformFee()); entity.setCreatedAt(fare.createdAt());
        return entity;
    }
    private Fare toDomain(FareJpaEntity entity) {
        return new Fare(entity.getFareId(), entity.getGroupId(), entity.getFareRuleId(), entity.getRuleVersion(),
                entity.getBaseAmount(), entity.getAmountPerPassenger(), entity.getPlatformFee(), entity.getCreatedAt());
    }
}
