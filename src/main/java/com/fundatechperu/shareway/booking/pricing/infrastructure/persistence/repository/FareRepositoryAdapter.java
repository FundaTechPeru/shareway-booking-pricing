package com.fundatechperu.shareway.booking.pricing.infrastructure.persistence.repository;

import com.fundatechperu.shareway.booking.pricing.domain.model.Fare;
import com.fundatechperu.shareway.booking.pricing.domain.repository.FareRepository;
import com.fundatechperu.shareway.booking.pricing.infrastructure.persistence.entity.FareJpaEntity;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public class FareRepositoryAdapter implements FareRepository {
    private final SpringDataFareRepository repository;
    public FareRepositoryAdapter(SpringDataFareRepository repository) { this.repository = repository; }

    @Override public Fare save(Fare fare) { return toDomain(repository.save(toEntity(fare))); }

    @Override public Optional<Fare> findByBookingIdAndFareType(UUID bookingId, String fareType) {
        return repository.findByBookingIdAndFareType(bookingId, fareType).map(this::toDomain);
    }

    private FareJpaEntity toEntity(Fare fare) {
        FareJpaEntity entity = new FareJpaEntity();
        entity.setFareId(fare.fareId());
        entity.setBookingId(fare.bookingId());
        entity.setRuleId(fare.ruleId());
        entity.setFareType(fare.fareType());
        entity.setDistanceKm(fare.distanceKm());
        entity.setGroupedPassengers(fare.groupedPassengers());
        entity.setAmount(fare.amount());
        entity.setCurrency(fare.currency());
        return entity;
    }

    private Fare toDomain(FareJpaEntity entity) {
        return new Fare(entity.getFareId(), entity.getBookingId(), entity.getRuleId(), entity.getFareType(),
                entity.getDistanceKm(), entity.getGroupedPassengers(), entity.getAmount(), entity.getCurrency());
    }
}
