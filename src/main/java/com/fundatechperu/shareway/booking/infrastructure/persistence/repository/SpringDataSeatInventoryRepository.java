package com.fundatechperu.shareway.booking.infrastructure.persistence.repository;

import com.fundatechperu.shareway.booking.infrastructure.persistence.entity.SeatInventoryJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataSeatInventoryRepository extends JpaRepository<SeatInventoryJpaEntity, UUID> {

    /** Row lock (SELECT ... FOR UPDATE) used by cancellations and the saga timeout job. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SeatInventoryJpaEntity s WHERE s.groupId = :groupId")
    Optional<SeatInventoryJpaEntity> findWithLockById(@Param("groupId") UUID groupId);
}
