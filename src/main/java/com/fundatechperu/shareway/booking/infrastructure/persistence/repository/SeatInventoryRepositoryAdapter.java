package com.fundatechperu.shareway.booking.infrastructure.persistence.repository;

import com.fundatechperu.shareway.booking.domain.model.SeatInventory;
import com.fundatechperu.shareway.booking.domain.repository.SeatInventoryRepository;
import com.fundatechperu.shareway.booking.infrastructure.persistence.entity.SeatInventoryJpaEntity;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class SeatInventoryRepositoryAdapter implements SeatInventoryRepository {

    private final SpringDataSeatInventoryRepository repository;

    public SeatInventoryRepositoryAdapter(SpringDataSeatInventoryRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<SeatInventory> findById(UUID groupId) {
        return repository.findById(groupId).map(this::toDomain);
    }

    /**
     * SELECT ... FOR UPDATE: cancellations and the timeout job serialize on the seat row of
     * the group (Sprint 2). Without the row lock, many simultaneous cancellations of the
     * same group exhaust the optimistic-lock retries of seat_inventories.version.
     */
    @Override
    public Optional<SeatInventory> findByIdForUpdate(UUID groupId) {
        return repository.findWithLockById(groupId).map(this::toDomain);
    }

    @Override
    public SeatInventory save(SeatInventory inventory) {
        return toDomain(repository.save(toEntity(inventory)));
    }

    private SeatInventoryJpaEntity toEntity(SeatInventory inventory) {
        SeatInventoryJpaEntity entity = new SeatInventoryJpaEntity();
        entity.setGroupId(inventory.getGroupId());
        entity.setCapacity(inventory.getCapacity());
        entity.setAvailableSeats(inventory.getAvailableSeats());
        entity.setVersion(inventory.getVersion());
        entity.setUpdatedAt(inventory.getUpdatedAt());
        return entity;
    }

    private SeatInventory toDomain(SeatInventoryJpaEntity entity) {
        return SeatInventory.restore(entity.getGroupId(), entity.getCapacity(), entity.getAvailableSeats(),
                entity.getVersion(), entity.getUpdatedAt());
    }
}
