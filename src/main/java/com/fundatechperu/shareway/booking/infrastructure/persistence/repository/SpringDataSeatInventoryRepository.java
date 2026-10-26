package com.fundatechperu.shareway.booking.infrastructure.persistence.repository;

import com.fundatechperu.shareway.booking.infrastructure.persistence.entity.SeatInventoryJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataSeatInventoryRepository extends JpaRepository<SeatInventoryJpaEntity, UUID> {
}
