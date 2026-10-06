package com.fundatechperu.shareway.bookingpricing.booking.infrastructure.persistence.repository;

import com.fundatechperu.shareway.bookingpricing.booking.domain.model.TripGroup;
import com.fundatechperu.shareway.bookingpricing.booking.domain.repository.TripGroupRepository;
import com.fundatechperu.shareway.bookingpricing.booking.infrastructure.persistence.entity.TripGroupJpaEntity;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class TripGroupRepositoryAdapter implements TripGroupRepository {
    private final SpringDataTripGroupRepository repository;

    public TripGroupRepositoryAdapter(SpringDataTripGroupRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<TripGroup> findById(UUID groupId) {
        return repository.findById(groupId).map(this::toDomain);
    }

    @Override
    public TripGroup save(TripGroup group) {
        TripGroupJpaEntity entity = new TripGroupJpaEntity();
        entity.setGroupId(group.getGroupId());
        entity.setCapacity(group.getCapacity());
        entity.setMinPassengers(group.getMinPassengers());
        entity.setAvailableSeats(group.getAvailableSeats());
        entity.setStatus(group.getStatus());
        entity.setVersion(group.getVersion());
        entity.setCreatedAt(group.getCreatedAt());
        return toDomain(repository.save(entity));
    }

    private TripGroup toDomain(TripGroupJpaEntity entity) {
        return TripGroup.restore(entity.getGroupId(), entity.getCapacity(), entity.getMinPassengers(),
                entity.getAvailableSeats(), entity.getStatus(), entity.getVersion(), entity.getCreatedAt());
    }
}
